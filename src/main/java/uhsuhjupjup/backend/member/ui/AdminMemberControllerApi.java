package uhsuhjupjup.backend.member.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@Tag(name = "관리자 - 회원", description = "회원 세션을 관리하는 관리자 API")
public interface AdminMemberControllerApi {

    @Operation(
            summary = "특정 회원 강제 로그아웃",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 지정한 회원의 모든 세션을 무효화합니다. 계정 탈취가 의심될 때 사용합니다.
                    - 성공하면 본문 없이 204를 반환합니다.

                    ### 본인 로그아웃과의 차이

                    - 회원이 스스로 하는 것은 `DELETE /api/members/me/sessions`입니다.
                    - 이 엔드포인트는 관리자가 다른 회원의 세션을 끊는 경로입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "세션 무효화 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/admin/members/3/sessions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 회원이 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "MEMBER_NOT_FOUND",
                                      "message": "회원을 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/members/3/sessions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void forceLogout(@Parameter(hidden = true) Member admin,
                     @Parameter(description = "대상 회원 ID", example = "3", required = true) Long id);
}
