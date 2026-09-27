package uhsuhjupjup.backend.oss.repo.ui;

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
import uhsuhjupjup.backend.oss.repo.ui.dto.AdminOssRepoResponse;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoRegisterRequest;

@Tag(name = "오픈소스 - 관리자 레포", description = """
        오픈소스 레포 카탈로그에 레포를 직접 담는 관리자 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 쓰임

        - 카탈로그 자동 수집이 생기기 전까지 레포를 담는 입구입니다.
        - 자동 수집이 생긴 뒤에는 수집 조건 밖의 레포를 담는 데 씁니다.
        """)
public interface AdminOssRepoControllerApi {

    @Operation(
            summary = "레포 등록",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - `fullName`(`owner/name`)으로 GitHub에서 레포를 조회해 확인한 뒤 저장하고, 201과 함께 저장한 레포를 반환합니다.
                    - 저장하는 이름은 입력값이 아니라 GitHub가 돌려준 정식 표기입니다. 대소문자를 다르게 넣거나 이름이 바뀐 레포의 옛 이름을 넣어도 지금의 정식 이름으로 저장됩니다.
                    - 새로 등록한 레포의 `status`는 `ACTIVE`입니다.

                    ### 요청 값

                    - `fullName`은 `owner/name` 형식입니다. 앞뒤 공백은 잘라 내고 검사합니다.
                    - owner는 1~39자의 영문, 숫자, 하이픈입니다. 하이픈으로 시작하거나 끝날 수 없고, 하이픈을 연달아 쓸 수 없습니다.
                    - name은 1~100자의 영문, 숫자, `.`, `-`, `_`입니다. `.`과 `..`은 쓸 수 없습니다.
                    - 형식이 틀리면 GitHub를 조회하지 않고 `VALIDATION_ERROR`로 400을 반환합니다.

                    ### 등록할 수 없는 레포

                    - 기여할 이슈를 받을 수 없는 레포는 거절합니다. 비공개면 `OSS_REPO_PRIVATE`, 이슈를 꺼 두었으면 `OSS_REPO_ISSUES_DISABLED`, archived면 `OSS_REPO_ARCHIVED`로 400을 반환합니다.
                    - 여러 이유에 걸리면 비공개, 이슈 꺼짐, archived 순으로 먼저 걸린 이유 하나만 반환합니다.
                    - GitHub에 없는 레포면 `GITHUB_REPO_NOT_FOUND`로 404를 반환합니다. 서버 토큰이 공개 레포만 읽을 수 있으면 비공개 레포도 이 404로 보입니다.
                    - 이미 등록된 레포면 `OSS_REPO_ALREADY_EXISTS`로 409를 반환합니다. GitHub id가 같거나 정식 이름이 대소문자만 다른 레포가 있는 경우입니다. 같은 레포를 동시에 여러 번 보내도 한 번만 저장되고 나머지는 409를 받습니다.
                    - 거절 판단(400)이 중복 판단(409)보다 먼저입니다. 이미 등록된 레포라도 지금 archived면 400을 반환합니다.

                    ### GitHub 조회 실패

                    - 서버에 GitHub 토큰이 설정되지 않았으면 `GITHUB_TOKEN_REQUIRED`로 503을 반환합니다.
                    - 한도 초과, 인증 실패, 무응답 같은 그 밖의 실패는 `GITHUB_UNAVAILABLE`로 503을 반환합니다. 자세한 이유는 서버 로그에 남습니다.
                    - GitHub가 응답하지 않으면 재시도를 마치고 503을 반환하기까지 30초 남짓 걸릴 수 있습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료",
                    content = @Content(schema = @Schema(implementation = AdminOssRepoResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "fullName 형식 오류, 또는 비공개, 이슈 꺼짐, archived 레포",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "형식 오류", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "fullName",
                                          "reason": "GitHub 레포 이름(owner/name) 형식이어야 합니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "비공개 레포", value = """
                                    {
                                      "code": "OSS_REPO_PRIVATE",
                                      "message": "비공개 레포는 등록할 수 없습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "이슈 꺼짐", value = """
                                    {
                                      "code": "OSS_REPO_ISSUES_DISABLED",
                                      "message": "이슈를 꺼 둔 레포는 등록할 수 없습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "archived 레포", value = """
                                    {
                                      "code": "OSS_REPO_ARCHIVED",
                                      "message": "보관(archived)된 레포는 등록할 수 없습니다.",
                                      "status": 400,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """)})),
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
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "GitHub에 없는 레포",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "GITHUB_REPO_NOT_FOUND",
                                      "message": "GitHub에서 레포를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 등록된 레포(GitHub id가 같거나 정식 이름이 대소문자만 다름)",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "OSS_REPO_ALREADY_EXISTS",
                                      "message": "이미 등록된 레포입니다.",
                                      "status": 409,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
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
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "GitHub 조회 불가", value = """
                                    {
                                      "code": "GITHUB_UNAVAILABLE",
                                      "message": "GitHub를 지금 조회할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                                      "status": 503,
                                      "path": "/api/admin/oss/repos",
                                      "timestamp": "2026-09-27T14:20:11.482"
                                    }
                                    """)}))
    })
    AdminOssRepoResponse register(@Parameter(hidden = true) Member admin, OssRepoRegisterRequest request);
}
