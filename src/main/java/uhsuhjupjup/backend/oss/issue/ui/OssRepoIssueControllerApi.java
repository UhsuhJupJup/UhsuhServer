package uhsuhjupjup.backend.oss.issue.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueCursor;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDifficultyFilter;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.ui.dto.OssIssuePageResponse;

@Tag(name = "오픈소스 - 이슈", description = "난이도를 판정한 오픈소스 이슈를 보는 API")
public interface OssRepoIssueControllerApi {

    @Operation(
            summary = "레포의 판정된 이슈 목록",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 대상

                    - 레포 하나의 이슈 가운데 현재 판정이 있고, 그 판정으로 추천에서 빼지 않은 이슈만 반환합니다. 현재 판정은 마지막으로 수집한 이슈 본문으로 매긴 판정 가운데 가장 나중에 저장한 것으로, 이슈 상세 조회(`GET /api/oss/issues/{issueId}`)와 기준이 같습니다. GitHub의 지금 본문이 아니라 수집이 마지막으로 저장한 본문이 기준입니다.
                    - 데이터는 이슈를 수집한 레포에만 있습니다. 정지되지 않았지만 이슈를 수집하지 않은 레포는 빈 목록입니다.
                    - 아직 판정하지 않은 이슈, 본문이 바뀌어 다시 판정을 기다리는 이슈, 판정에 실패했거나 판정할 수 없던 이슈(판정하려고 다시 받았을 때 닫혔거나 담당자가 생긴 이슈 등)는 나오지 않습니다. 본문이 바뀐 이슈는 새 본문으로 판정되면 다시 나옵니다.
                    - 질문, 스팸, 중복, 원인 미상으로 판정해 추천에서 뺀 이슈는 나오지 않습니다. 추천에서 뺐는지는 현재 판정으로만 봅니다. 같은 본문의 예전 판정이 추천 대상이었어도 현재 판정이 뺐으면 나오지 않고, 현재 판정이 추천 대상이면 예전 판정이 뺐어도 나옵니다.

                    ### 알려진 한계

                    - 판정이 있으면, 그 뒤에 닫혔거나 담당자나 PR이 붙은 이슈, GitHub에서 지워졌거나 다른 레포로 옮겨진 이슈도 목록에 남습니다. 수집은 열린 이슈만 읽고, 담당자가 있는 이슈는 갱신하지 않고, 지워진 이슈를 알아채지 못하며, 판정된 이슈는 본문이 바뀌기 전까지 다시 확인하지 않기 때문입니다. 이때 `githubUrl`은 GitHub에서 404가 나거나 다른 주소로 넘어갈 수 있습니다. 선점 추적이 들어오기 전까지의 알려진 한계이고, 선점 상태(열려 있는지, 담당자나 PR이 붙었는지)는 아직 응답에 없습니다.
                    - `title`은 마지막으로 수집한 GitHub 제목입니다. GitHub에서 제목을 고쳐도 다음 수집이 그 이슈를 다시 받기 전까지는 예전 제목입니다. 닫힌 이슈는 수집이 다시 받지 않고 담당자가 붙은 이슈는 수집이 갱신하지 않아서, 둘 다 예전 제목으로 남습니다. 256자가 넘는 제목은 잘려 있습니다.
                    - 판정은 GitHub에서 이슈를 다시 받아 그 본문으로 매깁니다. 수집한 뒤 판정하기 전에 본문이 고쳐졌다면, 새 판정이 저장돼도 다음 수집이 고친 본문을 받을 때까지 목록에 나오지 않습니다.

                    ### 필터

                    - `difficulty`로 난이도를 고릅니다. 값은 소문자 `easy`, `medium`, `hard`이고, 쉼표로 이어 여러 개를 보낼 수 있습니다(`difficulty=easy,medium`). 고른 난이도 가운데 하나인 이슈가 나옵니다.
                    - 같은 이름을 반복해 보내도 됩니다. `difficulty=easy&difficulty=medium`은 `difficulty=easy,medium`과 같습니다. `difficulty[]`처럼 이름이 다르면 받지 않고 무시하므로 모든 난이도가 나옵니다.
                    - 같은 값을 두 번 보내면 한 번으로 봅니다(`easy,easy`는 `easy`와 같습니다).
                    - 빼거나 값 전체를 비우면(`difficulty=`) 모든 난이도가 나옵니다.
                    - 쉼표로 나눈 값 가운데 빈 값이 있으면 400입니다(`easy,`, `,easy`, `easy,,medium`, `,`). 빈 값을 버리고 나머지로 거르지 않습니다. `,`처럼 전부 빈 값일 때 모든 난이도로 넓어지지 않게 하려는 것입니다.
                    - 대문자(`EASY`), 앞뒤에 공백이 붙은 값(`easy, medium`), 모르는 값도 400입니다.

                    ### 언어

                    - `lang`으로 `summary`의 언어를 고릅니다. 값은 소문자 `ko`, `en` 두 가지뿐입니다. 빼거나 비우면 `ko`입니다.
                    - 응답의 `lang`은 `summary`를 쓴 언어입니다. `title`은 언어에 따라 바뀌지 않습니다.

                    ### 정렬

                    - GitHub에 이슈가 올라온 시각(`githubCreatedAt`)이 최신인 이슈부터 반환합니다. 시각이 같으면 `id`가 큰 이슈가 먼저입니다.
                    - 판정한 시각이나 수집한 시각은 순서에 쓰지 않습니다.

                    ### 페이지 넘기기

                    - 한 번에 최대 `size`개를 반환하고, 마지막 페이지는 그보다 적을 수 있습니다. 기본 20개, 최대 50개입니다. 1보다 작으면 20, 50보다 크면 50으로 맞춥니다.
                    - 다음 페이지가 있으면 `nextCursor`에 커서를 담습니다. 다음 요청의 `cursor`에 그대로 넣고, `difficulty`와 `lang`은 같은 값으로 보냅니다.
                    - 커서는 마지막 이슈의 위치(GitHub에 올라온 시각과 `id`)만 담고 필터와 언어는 담지 않습니다. 다른 값을 보내도 오류가 아니라, 그 위치 다음부터 새 값으로 거른 목록이 나옵니다.
                    - 커서는 레포도 담지 않습니다. 다른 레포에 보내도 오류가 아니고, 그 레포의 목록에서 같은 위치 다음부터 보여 줍니다. 커서는 받은 레포에만 보냅니다.
                    - 마지막 페이지면 `nextCursor`는 `null`입니다.
                    - 커서는 서버가 만든 문자열입니다. 풀어 보거나 고쳐 쓰지 않고 그대로 돌려보냅니다.
                    - 넘기는 동안 새로 판정되거나 다시 판정된 이슈는, 이미 지나온 위치에 있으면 나오지 않고 아직 오지 않은 위치에 있으면 나옵니다. 넘기는 동안 다시 판정되어 조건에서 벗어난 이슈는 뒤 페이지에서 빠집니다. 정렬 기준(GitHub에 올라온 시각과 `id`)은 바뀌지 않으므로 같은 이슈가 두 번 나오지는 않습니다.
                    - 전체 개수는 반환하지 않습니다.

                    ### 응답

                    - `difficulty`는 `EASY`(쉬움), `MEDIUM`(중간), `HARD`(어려움) 중 하나입니다. 판정 모델이 구현량이나 기술 분야가 아니라 정보 완결성, 곧 이슈의 정보가 바로 손댈 수 있을 만큼 자세하고 분명한지로 고릅니다.
                    - `evidence`는 그 판단의 근거를 보여 줍니다. 난이도가 근거 요소로 계산되지는 않습니다.
                      - `problem`(문제), `reproduction`(재현), `cause`(원인), `fixDirection`(수정 방향)은 이슈에 그 정보가 있는 정도로, `PRESENT`(있음), `PARTIAL`(일부만 있거나 흐릿함), `ABSENT`(없음) 중 하나입니다.
                      - 다만 `EASY`는 네 요소가 모두 `PRESENT`일 때만 나옵니다. 판정 모델이 `EASY`를 골라도 하나라도 `PRESENT`가 아니면 `MEDIUM`으로 낮춰 저장합니다.
                      - `relatedPr`은 이슈가 관련 PR을 언급하거나 링크했는지이고, 그것만으로 난이도를 올리거나 내리지 않습니다. 이 이슈를 고치는 PR이 이미 열려 있는지(선점)와는 다릅니다.
                    - `summary`는 무슨 이슈인지에 대한 설명입니다. 판정 모델에는 두세 문장으로, 마크다운과 멘션 없이 쓰라고 지시하지만 보장하지는 않습니다.
                    - 보장하는 것은 이렇습니다. 비어 있지 않고, 최대 1,500자(유니코드 코드 포인트 기준)이며, URL(`http://`, `https://`, `www.`로 시작하는 주소)과 이메일 주소가 들어가지 않습니다.
                    - 판정 이유(`reason`)는 목록에 없습니다. 이슈 상세 조회에서 봅니다.
                    - `githubUrl`은 GitHub의 이슈 페이지 주소로, `https://github.com/` 뒤에 레포 이름(레포 상세의 `fullName`), `/issues/`, `number`를 붙인 값입니다.
                    - `githubCreatedAt`은 GitHub에서 이슈가 만들어진 시각, `gradedAt`은 판정한 시각입니다. 둘 다 한국 시간이고 시간대 표기가 없습니다.
                    - `id`는 이 서비스의 이슈 ID로, 이슈 상세 조회의 `issueId`에 넣습니다. GitHub의 이슈 번호는 `number`입니다.

                    ### 에러

                    - 없는 레포와 정지된 레포는 `OSS_REPO_NOT_FOUND`로 404를 반환합니다. 두 경우의 응답은 같습니다.
                    - 정지되지 않은 레포에 보여 줄 이슈가 없으면 404가 아니라 200이고, `items`는 빈 배열, `nextCursor`는 `null`입니다.
                    - `repoId`를 정수(Long 범위)로 읽을 수 없으면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 `repoId`를 담습니다.
                    - `difficulty`, `lang`이 위에서 정한 값이 아니거나 `cursor`를 해석할 수 없으면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 파라미터 이름을 담습니다. 이유는 `형식이 올바르지 않습니다.`입니다.
                    - `size`가 숫자가 아니면 `VALIDATION_ERROR`로 400을 반환합니다. int 범위를 넘는 값(예: `3000000000`)도 50으로 맞추지 않고 같은 400을 반환하며, `fieldErrors`의 이유는 `숫자여야 합니다.`입니다.
                    - 요청 값(`VALIDATION_ERROR`)을 먼저 확인하므로, 잘못된 요청 값은 없는 레포여도 400입니다. 여러 값이 잘못되었으면 `repoId`, `difficulty`, `lang`, `cursor`, `size` 순으로 먼저 걸린 하나만 담습니다.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 보여 줄 이슈가 없으면 `items`가 빈 배열이고 `nextCursor`는 null이다.",
                    content = @Content(
                            schema = @Schema(implementation = OssIssuePageResponse.class),
                            examples = {
                                    @ExampleObject(name = "한국어(기본), 다음 페이지 있음", value = """
                                            {
                                              "lang": "ko",
                                              "items": [
                                                {
                                                  "id": 501,
                                                  "number": 1284,
                                                  "title": "Retry interval in the config file is ignored",
                                                  "githubUrl": "https://github.com/acme/fastqueue/issues/1284",
                                                  "githubCreatedAt": "2026-10-05T09:12:44",
                                                  "difficulty": "EASY",
                                                  "evidence": {
                                                    "problem": "PRESENT",
                                                    "reproduction": "PRESENT",
                                                    "cause": "PRESENT",
                                                    "fixDirection": "PRESENT",
                                                    "relatedPr": false
                                                  },
                                                  "summary": "설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다. 로딩 순서를 바꾸고 회귀 테스트를 더하면 된다.",
                                                  "gradedAt": "2026-10-08T14:20:11"
                                                },
                                                {
                                                  "id": 498,
                                                  "number": 1279,
                                                  "title": "Workers sometimes linger after shutdown",
                                                  "githubUrl": "https://github.com/acme/fastqueue/issues/1279",
                                                  "githubCreatedAt": "2026-10-03T21:47:05",
                                                  "difficulty": "MEDIUM",
                                                  "evidence": {
                                                    "problem": "PRESENT",
                                                    "reproduction": "PRESENT",
                                                    "cause": "ABSENT",
                                                    "fixDirection": "PARTIAL",
                                                    "relatedPr": true
                                                  },
                                                  "summary": "SIGTERM을 받은 뒤 일부 워커 스레드가 종료되지 않는다. 종료 훅의 호출 순서부터 살펴봐야 한다.",
                                                  "gradedAt": "2026-10-08T14:21:37"
                                                }
                                              ],
                                              "nextCursor": "NDk4OjIwMjYtMTAtMDNUMjE6NDc6MDU"
                                            }
                                            """),
                                    @ExampleObject(name = "영어(lang=en), 마지막 페이지", value = """
                                            {
                                              "lang": "en",
                                              "items": [
                                                {
                                                  "id": 431,
                                                  "number": 1203,
                                                  "title": "Panic when the queue name is empty",
                                                  "githubUrl": "https://github.com/acme/fastqueue/issues/1203",
                                                  "githubCreatedAt": "2026-09-30T08:02:51",
                                                  "difficulty": "HARD",
                                                  "evidence": {
                                                    "problem": "PRESENT",
                                                    "reproduction": "ABSENT",
                                                    "cause": "ABSENT",
                                                    "fixDirection": "ABSENT",
                                                    "relatedPr": false
                                                  },
                                                  "summary": "Creating a queue with an empty name crashes the broker. How to reproduce it and where it fails are not known yet.",
                                                  "gradedAt": "2026-10-08T14:22:05"
                                                }
                                              ],
                                              "nextCursor": null
                                            }
                                            """),
                                    @ExampleObject(name = "보여 줄 이슈 없음", value = """
                                            {
                                              "lang": "ko",
                                              "items": [],
                                              "nextCursor": null
                                            }
                                            """)})),
            @ApiResponse(
                    responseCode = "400",
                    description = "정수(Long 범위)로 읽을 수 없는 레포 ID, 잘못된 난이도, 언어, 커서, 또는 숫자가 아닌 크기",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "숫자가 아닌 레포 ID", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos/abc/issues",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "repoId",
                                          "reason": "숫자여야 합니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "잘못된 난이도", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos/10/issues",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "difficulty",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "지원하지 않는 언어", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos/10/issues",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "lang",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "잘못된 커서", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos/10/issues",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "cursor",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "숫자가 아닌 크기", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos/10/issues",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "size",
                                          "reason": "숫자여야 합니다."
                                        }
                                      ]
                                    }
                                    """)})),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 레포, 또는 정지된 레포",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_REPO_NOT_FOUND",
                                      "message": "레포를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/oss/repos/10/issues",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)))
    })
    OssIssuePageResponse repoIssues(
            @Parameter(description = "레포 ID. 레포 탐색과 상세의 `id`(GitHub id가 아님)", example = "10", required = true)
            Long repoId,
            @Parameter(description = "난이도. 소문자 easy, medium, hard를 쉼표로 이어 여러 개. 빼거나 비우면 전부",
                    explode = Explode.FALSE,
                    array = @ArraySchema(uniqueItems = true,
                            schema = @Schema(type = "string", allowableValues = {"easy", "medium", "hard"})))
            OssIssueDifficultyFilter difficulty,
            @Parameter(description = "summary의 언어. 빼거나 비우면 ko",
                    schema = @Schema(type = "string", allowableValues = {"ko", "en"}, defaultValue = "ko"))
            OssIssueLanguage lang,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 보내지 않는다", schema = @Schema(type = "string"))
            OssIssueCursor cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20") Integer size);
}
