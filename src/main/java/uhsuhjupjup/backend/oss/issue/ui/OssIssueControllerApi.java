package uhsuhjupjup.backend.oss.issue.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.ui.dto.OssIssueDetailResponse;

@Tag(name = "오픈소스 - 이슈", description = "난이도를 판정한 오픈소스 이슈를 보는 API")
public interface OssIssueControllerApi {

    @Operation(
            summary = "이슈 상세 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 대상

                    - 현재 판정이 있는 이슈만 반환합니다. 현재 판정은 마지막으로 수집한 이슈 본문으로 매긴 판정 가운데 가장 나중에 저장한 것입니다. GitHub의 지금 본문이 아니라 수집이 마지막으로 저장한 본문이 기준입니다.
                    - 마지막으로 수집한 본문의 판정이 없으면 404입니다. 아직 판정하지 않은 이슈, 본문이 바뀌어 다시 판정을 기다리는 이슈, 판정에 실패했거나 판정할 수 없던 이슈(판정하려고 다시 받았을 때 닫혔거나 담당자가 생긴 이슈 등)가 여기에 듭니다. 본문이 바뀐 이슈는 새 본문으로 판정되면 다시 보입니다.
                    - 판정은 GitHub에서 이슈를 다시 받아 그 본문으로 매깁니다. 수집한 뒤 판정하기 전에 본문이 고쳐졌다면, 새 판정이 저장돼도 다음 수집이 고친 본문을 받을 때까지 404입니다.
                    - 질문, 스팸, 중복, 원인 미상으로 판정해 추천에서 뺀 이슈도 404입니다.
                    - 이슈의 레포가 정지되었으면 404입니다.
                    - 판정이 있으면, 그 뒤에 닫혔거나 담당자나 PR이 붙은 이슈, GitHub에서 지워졌거나 다른 레포로 옮겨진 이슈도 반환합니다. 수집은 열린 이슈만 읽고, 담당자가 있는 이슈는 갱신하지 않고, 지워진 이슈를 알아채지 못하며, 판정된 이슈는 본문이 바뀌기 전까지 다시 확인하지 않기 때문입니다. 이때 `githubUrl`은 GitHub에서 404가 나거나 다른 주소로 넘어갈 수 있습니다. 선점 추적이 들어오기 전까지의 알려진 한계이고, 선점 상태(열려 있는지, 담당자나 PR이 붙었는지)는 아직 응답에 없습니다.

                    ### 언어

                    - `lang`으로 `reason`과 `summary`의 언어를 고릅니다. 값은 소문자 `ko`, `en` 두 가지뿐입니다. 빼거나 비우면 `ko`입니다.
                    - 응답의 `lang`은 `reason`과 `summary`를 쓴 언어입니다.
                    - `title`은 마지막으로 수집한 GitHub 제목 그대로이고 언어에 따라 바뀌지 않습니다. 256자가 넘는 제목은 잘려 있습니다.

                    ### 응답

                    - `difficulty`는 `EASY`(쉬움), `MEDIUM`(중간), `HARD`(어려움) 중 하나입니다. 판정 모델이 구현량이나 기술 분야가 아니라 정보 완결성, 곧 이슈의 정보가 바로 손댈 수 있을 만큼 자세하고 분명한지로 고릅니다.
                    - `evidence`는 그 판단의 근거를 보여 줍니다. 난이도가 근거 요소로 계산되지는 않습니다.
                      - `problem`(문제), `reproduction`(재현), `cause`(원인), `fixDirection`(수정 방향)은 이슈에 그 정보가 있는 정도로, `PRESENT`(있음), `PARTIAL`(일부만 있거나 흐릿함), `ABSENT`(없음) 중 하나입니다.
                      - 다만 `EASY`는 네 요소가 모두 `PRESENT`일 때만 나옵니다. 판정 모델이 `EASY`를 골라도 하나라도 `PRESENT`가 아니면 `MEDIUM`으로 낮춰 저장합니다.
                      - `relatedPr`은 이슈가 관련 PR을 언급하거나 링크했는지이고, 그것만으로 난이도를 올리거나 내리지 않습니다. 이 이슈를 고치는 PR이 이미 열려 있는지(선점)와는 다릅니다.
                    - `reason`은 판정 이유, `summary`는 무슨 이슈인지에 대한 설명입니다. 판정 모델에는 `reason`을 한 문장, `summary`를 두세 문장으로, 마크다운과 멘션 없이 쓰라고 지시하지만 보장하지는 않습니다.
                    - 보장하는 것은 이렇습니다. 둘 다 비어 있지 않고, `reason`은 최대 500자, `summary`는 최대 1,500자(유니코드 코드 포인트 기준)이며, URL(`http://`, `https://`, `www.`로 시작하는 주소)과 이메일 주소가 들어가지 않습니다.
                    - `githubUrl`은 GitHub의 이슈 페이지 주소로, `https://github.com/` 뒤에 `repo.fullName`, `/issues/`, `number`를 붙인 값입니다.
                    - `githubCreatedAt`은 GitHub에서 이슈가 만들어진 시각, `gradedAt`은 판정한 시각입니다. 둘 다 한국 시간이고 시간대 표기가 없습니다.
                    - `id`는 이 서비스의 이슈 ID이고, GitHub의 이슈 번호는 `number`입니다.

                    ### 에러

                    - 없는 이슈, 현재 판정이 없는 이슈, 추천에서 뺀 이슈, 정지된 레포의 이슈는 모두 `OSS_ISSUE_NOT_FOUND`로 404를 반환합니다. 네 경우의 응답은 같습니다.
                    - `issueId`를 정수(Long 범위)로 읽을 수 없으면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 `issueId`를 담습니다.
                    - `lang`이 `ko`, `en`이 아니면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 `lang`을 담습니다. 대소문자만 달라도(`KO`) 400입니다.
                    - 요청 값(`VALIDATION_ERROR`)을 먼저 확인하므로, 잘못된 `lang`은 없는 이슈여도 400입니다.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = OssIssueDetailResponse.class),
                            examples = {
                                    @ExampleObject(name = "한국어(기본)", value = """
                                            {
                                              "id": 501,
                                              "number": 1284,
                                              "title": "Retry interval in the config file is ignored",
                                              "githubUrl": "https://github.com/acme/fastqueue/issues/1284",
                                              "githubCreatedAt": "2026-10-05T09:12:44",
                                              "repo": {
                                                "id": 10,
                                                "fullName": "acme/fastqueue"
                                              },
                                              "difficulty": "EASY",
                                              "evidence": {
                                                "problem": "PRESENT",
                                                "reproduction": "PRESENT",
                                                "cause": "PRESENT",
                                                "fixDirection": "PRESENT",
                                                "relatedPr": false
                                              },
                                              "lang": "ko",
                                              "reason": "재현 테스트와 원인 함수, 한 줄 수정 제안까지 본문에 있다.",
                                              "summary": "설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다. 로딩 순서를 바꾸고 회귀 테스트를 더하면 된다.",
                                              "gradedAt": "2026-10-08T14:20:11"
                                            }
                                            """),
                                    @ExampleObject(name = "영어(lang=en)", value = """
                                            {
                                              "id": 501,
                                              "number": 1284,
                                              "title": "Retry interval in the config file is ignored",
                                              "githubUrl": "https://github.com/acme/fastqueue/issues/1284",
                                              "githubCreatedAt": "2026-10-05T09:12:44",
                                              "repo": {
                                                "id": 10,
                                                "fullName": "acme/fastqueue"
                                              },
                                              "difficulty": "EASY",
                                              "evidence": {
                                                "problem": "PRESENT",
                                                "reproduction": "PRESENT",
                                                "cause": "PRESENT",
                                                "fixDirection": "PRESENT",
                                                "relatedPr": false
                                              },
                                              "lang": "en",
                                              "reason": "The body has a failing test, the faulty function, and a one-line fix.",
                                              "summary": "Settings are read in an order that resets retry.interval to its default. Reordering the loading and adding a regression test fixes it.",
                                              "gradedAt": "2026-10-08T14:20:11"
                                            }
                                            """)})),
            @ApiResponse(
                    responseCode = "400",
                    description = "정수(Long 범위)로 읽을 수 없는 이슈 ID, 또는 ko, en이 아닌 언어",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "숫자가 아닌 이슈 ID", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/issues/abc",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "issueId",
                                          "reason": "숫자여야 합니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "지원하지 않는 언어", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/issues/501",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "lang",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """)})),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 이슈, 현재 판정이 없는 이슈, 추천에서 뺀 이슈, 또는 정지된 레포의 이슈",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_ISSUE_NOT_FOUND",
                                      "message": "이슈를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/oss/issues/501",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)))
    })
    OssIssueDetailResponse detail(
            @Parameter(description = "이슈 ID. GitHub의 이슈 번호나 id가 아니다", example = "501", required = true)
            Long issueId,
            @Parameter(description = "reason과 summary의 언어. 빼거나 비우면 ko",
                    schema = @Schema(type = "string", allowableValues = {"ko", "en"}, defaultValue = "ko"))
            OssIssueLanguage lang);
}
