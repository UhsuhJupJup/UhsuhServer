package uhsuhjupjup.backend.oss.pipeline.grading.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.pipeline.grading.ui.dto.AdminOssIssueGradingResponse;

@Tag(name = "오픈소스 - 관리자 이슈 판정", description = """
        등록된 오픈소스 레포 하나의 이슈 난이도 판정을 손으로 돌리는 관리자 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 쓰임

        - 30분 주기 판정을 켜기 전에 실제 레포 이슈로 판정 품질과 이슈당 비용을 확인합니다.
        - 판정 결과(난이도, 근거, 이유, 요약)는 응답에 없습니다. 응답은 실행 요약입니다.
        - LLM 사용량도 응답에 없습니다. 서버 로그의 `이슈 판정 응답 ... issueId=` 줄로 이슈마다 토큰 수를 확인합니다.
        - 같은 레포의 판정은 한 번에 하나만 돕니다.
        """)
public interface AdminOssIssueGradingControllerApi {

    @Operation(
            summary = "레포 이슈 판정",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 레포의 판정 후보에서 최근에 만들어진 이슈부터 `limit`개를 고릅니다. 후보는 지금 본문으로 판정된 적이 없고, 같은 본문으로 판정에 3번 실패하지 않은 이슈입니다.
                    - 고른 이슈마다 GitHub에서 이슈를 다시 읽고, 판정할 수 있으면 LLM(Claude, 실패하면 GPT)으로 판정해 저장합니다.
                    - 요청 안에서 판정을 끝까지 마친 뒤 200과 함께 실행 요약을 반환합니다.
                    - 이슈마다 GitHub 조회와 LLM 호출이 있어 응답까지 수 분이 걸릴 수 있습니다. LLM이 느리면 재시도 때문에 이슈 하나에 1분 남짓, GPT로 넘어가면 2분 남짓 걸립니다.
                    - 시작한 지 10분이 지나면 다음 이슈를 시작하지 않고 멈춥니다. 한 번 실행은 길어야 12분 남짓입니다.
                    - 응답이 오래 걸려 앞단에서 504(게이트웨이 타임아웃)를 받아도 서버의 판정은 끝까지 돕니다. 끝나기 전에 다시 부르면 409를 받습니다.
                    - GitHub나 LLM 때문에 중간에 멈춰도 200입니다. 멈춘 이유는 `stopReason`에 있고, 멈추기 전에 판정한 이슈는 저장되어 수에 들어 있습니다.

                    ### 멈춘 이유(`stopReason`)

                    - `COMPLETED`: 고른 이슈를 끝까지 봤고 남은 후보가 없습니다.
                    - `ISSUE_LIMIT`: `limit`개를 끝까지 봤고 후보가 더 남았습니다. 다시 호출하면 이어서 판정합니다.
                    - `TIME_LIMIT`: 시작한 지 10분이 지나 멈췄습니다.
                    - `GRADER_UNAVAILABLE`: Claude와 GPT가 모두 응답하지 않거나 서킷이 열려 있습니다. 둘 다 꺼져 있어도(`OSS_GRADING_CLAUDE_ENABLED`, `OSS_GRADING_GPT_ENABLED`) 이 이유로 멈춥니다.
                    - `GRADER_REJECTED`: LLM이 요청을 거절했습니다(키나 설정 오류 같은 4xx).
                    - `GITHUB_NOT_CONFIGURED`: 서버에 GitHub 토큰이 없습니다.
                    - `GITHUB_UNAUTHORIZED`: GitHub가 토큰을 거절했습니다.
                    - `GITHUB_RATE_LIMITED`: GitHub 한도를 다 썼습니다.
                    - `GITHUB_UNAVAILABLE`: GitHub가 연달아 두 번 응답하지 않았습니다.
                    - `INTERRUPTED`: 서버가 종료되는 중이라 멈췄습니다.
                    - `COMPLETED`, `ISSUE_LIMIT`, `TIME_LIMIT` 말고는 멈춘 원인이 서버 로그에 WARN으로 남습니다.

                    ### 응답 값

                    - `selected`: 이번에 고른 이슈 수입니다. `limit`보다 클 수 없습니다.
                    - `graded`: 판정해 저장한 수입니다.
                    - `held`: 멈춘 순간 보던 이슈 수(0이나 1)입니다. 판정도 실패도 남기지 않아 다음 실행이 다시 고릅니다.
                    - `notStarted`: 멈추는 바람에 손대지 못한 수입니다.
                    - `sameBodySkipped`: 지금 본문으로 이미 판정이 있어 건너뛴 수입니다.
                    - `failureLimitSkipped`: 같은 본문으로 이미 3번 실패해 건너뛴 수입니다.
                    - `githubSkipped`: GitHub가 한 번 응답하지 않아 건너뛴 수입니다. 실패로 세지 않습니다.
                    - `ungradable`: 판정할 수 없어 건너뛴 수를 이유별로 셉니다. 아홉 키가 0을 포함해 항상 있습니다.
                      - `GONE`(지워짐), `TRANSFERRED`(다른 레포로 옮겨짐), `BLOCKED`(GitHub가 이 이슈를 거절), `ID_MISMATCH`(같은 번호가 다른 이슈), `CLOSED`(닫힘), `PULL_REQUEST`(PR), `BOT_AUTHOR`(봇이 씀), `ASSIGNED`(담당자 있음), `DELETED`(판정하는 사이 지워짐)
                      - `DELETED`를 뺀 나머지는 그 이슈의 실패 횟수를 1 올립니다.
                    - `failed`: LLM이 그 이슈만 판정하지 못한 수를 이유별로 셉니다. 네 키가 0을 포함해 항상 있고, 실패 횟수를 1 올립니다.
                      - `REFUSED`(판정 거절), `TRUNCATED`(출력이 잘림), `INVALID_OUTPUT`(규칙을 어긴 출력), `INVALID_INPUT`(입력을 만들지 못함)
                    - `selected`는 `graded`, `held`, `notStarted`, `sameBodySkipped`, `failureLimitSkipped`, `githubSkipped`, `ungradable`의 합, `failed`의 합을 모두 더한 값과 같습니다.

                    ### 동시 판정

                    - 같은 레포의 판정이 진행 중이면 시작하지 않고 `OSS_ISSUE_GRADING_IN_PROGRESS`로 409를 반환합니다. 앞의 판정이 끝난 뒤 다시 호출하면 됩니다.
                    - 다른 레포의 판정, 같은 레포의 수집과는 동시에 돌 수 있습니다.
                    - 판정 도중 서버가 멈추면 잠금이 남지만 15분 뒤 저절로 풀립니다.

                    ### 에러

                    - `limit`이 정수가 아니거나(예: `abc`, `5.5`) int 범위를 넘으면(예: `3000000000`) `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`의 이유는 `숫자여야 합니다.`입니다.
                    - `limit`이 int 범위 안에서 1보다 작거나 20보다 크면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`의 이유는 `1 이상이어야 합니다.`나 `20 이하여야 합니다.`입니다.
                    - `repoId`가 숫자가 아니면 `VALIDATION_ERROR`로 400을 반환합니다.
                    - 없거나 정지된 레포면 `OSS_REPO_NOT_FOUND`로 404를 반환합니다.
                    - 권한(403), 요청 값(400), 레포(404), 진행 중(409) 순으로 확인하고, 먼저 걸린 것 하나만 반환합니다.
                    - 드물게 판정을 저장하다 DB 오류가 나면 `INTERNAL_ERROR`로 500을 반환합니다. 그 전에 저장한 판정은 남습니다. 다시 호출하면 됩니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "판정 실행 끝. 중간에 멈췄으면 `stopReason`에 이유가 있다",
                    content = @Content(schema = @Schema(implementation = AdminOssIssueGradingResponse.class),
                            examples = {
                                    @ExampleObject(name = "끝까지 판정", value = """
                                            {
                                              "stopReason": "COMPLETED",
                                              "selected": 4,
                                              "graded": 3,
                                              "held": 0,
                                              "notStarted": 0,
                                              "sameBodySkipped": 0,
                                              "failureLimitSkipped": 0,
                                              "githubSkipped": 0,
                                              "ungradable": {
                                                "GONE": 0,
                                                "TRANSFERRED": 0,
                                                "BLOCKED": 0,
                                                "ID_MISMATCH": 0,
                                                "CLOSED": 1,
                                                "PULL_REQUEST": 0,
                                                "BOT_AUTHOR": 0,
                                                "ASSIGNED": 0,
                                                "DELETED": 0
                                              },
                                              "failed": {
                                                "REFUSED": 0,
                                                "TRUNCATED": 0,
                                                "INVALID_OUTPUT": 0,
                                                "INVALID_INPUT": 0
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "개수 한도", value = """
                                            {
                                              "stopReason": "ISSUE_LIMIT",
                                              "selected": 5,
                                              "graded": 4,
                                              "held": 0,
                                              "notStarted": 0,
                                              "sameBodySkipped": 0,
                                              "failureLimitSkipped": 0,
                                              "githubSkipped": 0,
                                              "ungradable": {
                                                "GONE": 0,
                                                "TRANSFERRED": 0,
                                                "BLOCKED": 0,
                                                "ID_MISMATCH": 0,
                                                "CLOSED": 0,
                                                "PULL_REQUEST": 0,
                                                "BOT_AUTHOR": 0,
                                                "ASSIGNED": 0,
                                                "DELETED": 0
                                              },
                                              "failed": {
                                                "REFUSED": 0,
                                                "TRUNCATED": 0,
                                                "INVALID_OUTPUT": 1,
                                                "INVALID_INPUT": 0
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "LLM 장애로 멈춤", value = """
                                            {
                                              "stopReason": "GRADER_UNAVAILABLE",
                                              "selected": 5,
                                              "graded": 2,
                                              "held": 1,
                                              "notStarted": 2,
                                              "sameBodySkipped": 0,
                                              "failureLimitSkipped": 0,
                                              "githubSkipped": 0,
                                              "ungradable": {
                                                "GONE": 0,
                                                "TRANSFERRED": 0,
                                                "BLOCKED": 0,
                                                "ID_MISMATCH": 0,
                                                "CLOSED": 0,
                                                "PULL_REQUEST": 0,
                                                "BOT_AUTHOR": 0,
                                                "ASSIGNED": 0,
                                                "DELETED": 0
                                              },
                                              "failed": {
                                                "REFUSED": 0,
                                                "TRUNCATED": 0,
                                                "INVALID_OUTPUT": 0,
                                                "INVALID_INPUT": 0
                                              }
                                            }
                                            """)})),
            @ApiResponse(
                    responseCode = "400",
                    description = "범위를 벗어나거나 숫자가 아닌 limit, 숫자가 아닌 repoId",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos/10/issues/grade",
                                      "timestamp": "2026-10-08T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "limit",
                                          "reason": "20 이하여야 합니다."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "FORBIDDEN",
                                      "message": "접근 권한이 없습니다.",
                                      "status": 403,
                                      "path": "/api/admin/oss/repos/10/issues/grade",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "없거나 정지된 레포",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_REPO_NOT_FOUND",
                                      "message": "레포를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/oss/repos/10/issues/grade",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "409",
                    description = "같은 레포의 판정이 진행 중",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_ISSUE_GRADING_IN_PROGRESS",
                                      "message": "이 레포의 이슈 판정이 이미 진행 중입니다. 잠시 뒤 다시 시도해 주세요.",
                                      "status": 409,
                                      "path": "/api/admin/oss/repos/10/issues/grade",
                                      "timestamp": "2026-10-08T14:20:11.482"
                                    }
                                    """)))
    })
    AdminOssIssueGradingResponse gradeIssues(
            @Parameter(hidden = true) Member admin,
            @Parameter(description = "레포 ID", example = "10", required = true) Long repoId,
            @Parameter(description = "이번에 판정할 최대 이슈 수. 기본 5, 1~20", example = "5")
            @Min(value = 1, message = "1 이상이어야 합니다.")
            @Max(value = 20, message = "20 이하여야 합니다.") int limit);
}
