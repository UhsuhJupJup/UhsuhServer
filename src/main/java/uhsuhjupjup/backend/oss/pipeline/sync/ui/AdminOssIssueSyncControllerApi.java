package uhsuhjupjup.backend.oss.pipeline.sync.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.pipeline.sync.ui.dto.AdminOssIssueSyncResponse;

@Tag(name = "오픈소스 - 관리자 이슈 수집", description = """
        등록된 오픈소스 레포 하나의 이슈 수집을 손으로 돌리는 관리자 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 쓰임

        - 30분 주기 수집을 켜기 전에 실제 레포로 수집이 되는지 확인합니다.
        - GitHub 한도를 얼마나 쓰는지는 응답에 없습니다. 이슈 목록을 읽을 때마다 서버 로그에 남는 `GitHub 한도 ... 남음` 줄로 확인합니다.
        - 같은 레포의 수집은 한 번에 하나만 돕니다.
        """)
public interface AdminOssIssueSyncControllerApi {

    @Operation(
            summary = "레포 이슈 수집",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 레포의 열린 이슈를 GitHub에서 읽어 저장합니다. 요청 안에서 수집을 끝까지 마친 뒤 200과 함께 결과 요약을 반환합니다.
                    - 처음 수집하는 레포는 최근 7일 안에 바뀐 이슈를 읽습니다. 그 뒤로는 지난 수집에서 본 가장 늦은 수정 시각부터 읽습니다.
                    - PR, 봇이 쓴 이슈, 담당자가 있는 이슈는 저장하지 않습니다.
                    - 이슈 본문은 저장하지 않고 해시만 남깁니다.
                    - 같은 이슈는 다시 읽어도 한 번만 저장합니다. 연달아 호출해도 중복이 생기지 않습니다.
                    - GitHub가 느리거나 응답하지 않으면 재시도 때문에 페이지 하나에 30초 남짓 걸릴 수 있습니다. 여러 페이지를 읽는 레포는 응답이 수 분까지 늦어질 수 있습니다.
                    - 응답이 오래 걸려 앞단에서 504(게이트웨이 타임아웃)를 받아도 서버의 수집은 끝까지 돕니다. 끝나기 전에 다시 부르면 409를 받습니다.

                    ### 응답 값

                    - `notModified`: 지난 수집 뒤로 바뀐 것이 없으면 `true`입니다. 이때 나머지 수는 모두 0입니다.
                    - `incompleteReason`: 끝까지 읽었으면 `null`입니다.
                      - `PAGE_LIMIT`: 한 번에 읽는 상한(10페이지, 1,000건)에 걸려 앞부분만 저장했습니다. 다음 수집이 이어서 읽습니다.
                      - `PAGE_UNAVAILABLE`: 중간 페이지가 재시도 끝에도 응답하지 않아 앞부분만 저장했습니다. 이때도 200이지만 레포의 연속 오류 횟수가 1 올라갑니다.
                    - `received`: GitHub에서 받은 수입니다. PR도 들어 있습니다.
                    - `excluded`: 저장하지 않은 수를 이유별로 셉니다. `PULL_REQUEST`(PR), `BOT_AUTHOR`(봇이 씀), `ASSIGNED`(담당자 있음) 세 키가 0을 포함해 항상 있습니다.
                    - `created`: 새로 저장한 이슈 수입니다.
                    - `bodyChanged`: 이미 있던 이슈 중 본문이 바뀐 수입니다.
                    - `bodyUnchanged`: 이미 있던 이슈 중 본문이 그대로인 수입니다. 제목이나 번호가 바뀌었으면 그것만 갱신합니다.
                    - `received`는 `excluded`의 합과 `created`, `bodyChanged`, `bodyUnchanged`를 모두 더한 값과 같습니다.

                    ### 동시 수집

                    - 같은 레포의 수집이 진행 중이면 시작하지 않고 `OSS_ISSUE_SYNC_IN_PROGRESS`로 409를 반환합니다. 앞의 수집이 끝난 뒤 다시 호출하면 됩니다.
                    - 다른 레포의 수집과는 동시에 돌 수 있습니다.
                    - 수집 도중 서버가 멈추면 잠금이 남지만 20분 뒤 저절로 풀립니다.

                    ### 에러

                    - `repoId`가 숫자가 아니면 `VALIDATION_ERROR`로 400을 반환합니다.
                    - 없거나 정지된 레포면 `OSS_REPO_NOT_FOUND`로 404를 반환합니다.
                    - 서버에 GitHub 토큰이 설정되지 않았으면 `GITHUB_TOKEN_REQUIRED`로 503을 반환합니다.
                    - 한도 초과, 인증 실패, 무응답, 레포 접근 거절 같은 그 밖의 GitHub 실패는 `GITHUB_UNAVAILABLE`로 503을 반환합니다. 자세한 이유는 서버 로그에 남습니다.
                    - 레포 접근 거절, 잘못된 응답, 무응답처럼 레포 쪽 실패는 레포의 연속 오류 횟수를 1 올립니다. 토큰, 인증, 한도 문제는 올리지 않습니다.
                    - 권한(403), 요청 값(400), 레포(404), 진행 중(409), GitHub(503) 순으로 확인하고, 먼저 걸린 것 하나만 반환합니다.
                    - 드물게, 레포 사이로 옮겨진 이슈를 다른 레포의 수집과 동시에 저장하다 겹치면 한 번 더 저장하는데, 그것까지 실패하면 `RESOURCE_ALREADY_EXISTS`로 409나 `INTERNAL_ERROR`로 500을 반환할 수 있습니다. 다시 호출하면 됩니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수집 완료. 끝까지 읽지 못했으면 `incompleteReason`에 이유가 있다",
                    content = @Content(schema = @Schema(implementation = AdminOssIssueSyncResponse.class), examples = {
                            @ExampleObject(name = "새 이슈 저장", value = """
                                    {
                                      "notModified": false,
                                      "incompleteReason": null,
                                      "received": 42,
                                      "excluded": {
                                        "PULL_REQUEST": 10,
                                        "BOT_AUTHOR": 2,
                                        "ASSIGNED": 3
                                      },
                                      "created": 20,
                                      "bodyChanged": 1,
                                      "bodyUnchanged": 6
                                    }
                                    """),
                            @ExampleObject(name = "바뀐 것 없음", value = """
                                    {
                                      "notModified": true,
                                      "incompleteReason": null,
                                      "received": 0,
                                      "excluded": {
                                        "PULL_REQUEST": 0,
                                        "BOT_AUTHOR": 0,
                                        "ASSIGNED": 0
                                      },
                                      "created": 0,
                                      "bodyChanged": 0,
                                      "bodyUnchanged": 0
                                    }
                                    """),
                            @ExampleObject(name = "중간 페이지 실패", value = """
                                    {
                                      "notModified": false,
                                      "incompleteReason": "PAGE_UNAVAILABLE",
                                      "received": 300,
                                      "excluded": {
                                        "PULL_REQUEST": 120,
                                        "BOT_AUTHOR": 8,
                                        "ASSIGNED": 30
                                      },
                                      "created": 140,
                                      "bodyChanged": 0,
                                      "bodyUnchanged": 2
                                    }
                                    """)})),
            @ApiResponse(
                    responseCode = "400",
                    description = "숫자가 아닌 repoId",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos/abc/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "repoId",
                                          "reason": "숫자여야 합니다."
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
                                      "path": "/api/admin/oss/repos/10/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482"
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
                                      "path": "/api/admin/oss/repos/10/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "409",
                    description = "같은 레포의 수집이 진행 중",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_ISSUE_SYNC_IN_PROGRESS",
                                      "message": "이 레포의 이슈 수집이 이미 진행 중입니다. 잠시 뒤 다시 시도해 주세요.",
                                      "status": 409,
                                      "path": "/api/admin/oss/repos/10/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "503",
                    description = "GitHub 토큰이 없거나 GitHub를 조회할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "토큰 없음", value = """
                                    {
                                      "code": "GITHUB_TOKEN_REQUIRED",
                                      "message": "GitHub 토큰이 설정되지 않아 조회할 수 없습니다.",
                                      "status": 503,
                                      "path": "/api/admin/oss/repos/10/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "GitHub 조회 불가", value = """
                                    {
                                      "code": "GITHUB_UNAVAILABLE",
                                      "message": "GitHub를 지금 조회할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                                      "status": 503,
                                      "path": "/api/admin/oss/repos/10/issues/sync",
                                      "timestamp": "2026-10-06T14:20:11.482"
                                    }
                                    """)}))
    })
    AdminOssIssueSyncResponse syncIssues(@Parameter(hidden = true) Member admin,
                                         @Parameter(description = "레포 ID", example = "10", required = true) Long repoId);
}
