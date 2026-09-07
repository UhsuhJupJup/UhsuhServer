package uhsuhjupjup.backend.member.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.member.ui.dto.LoginRequest;
import uhsuhjupjup.backend.member.ui.dto.MemberResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "인증", description = "Firebase 로그인 결과를 백엔드 회원과 연결하는 API")
public interface AuthControllerApi {

    @Operation(
            summary = "로그인, 회원 동기화",
            description = """
                    ### 호출 시점

                    - Firebase 로그인 직후 반드시 한 번 호출해야 합니다.
                    - 이 호출을 생략하면 백엔드에 회원이 생성되지 않아 구독과 노트 기능이 401로 막힙니다.

                    ### 동작

                    - find-or-register 방식입니다. 회원이 이미 있으면 그대로 반환하고, 없으면 새로 생성합니다.
                    - 여러 번 호출해도 안전합니다. 기존 회원이든 신규 회원이든 200을 반환합니다.

                    ### 요청 본문

                    - 본문은 선택입니다. 생략하면 동의 상태를 변경하지 않습니다.
                    - 가입과 동시에 메일 수신 동의를 받으려면 `{"consent": true}`를 보냅니다.

                    ### 인증

                    - `Authorization: Bearer {Firebase ID 토큰}` 헤더가 필요합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공. 기존 회원이든 신규 회원이든 200을 준다",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class))),
            @ApiResponse(responseCode = "401", description = "토큰이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "EXPIRED_ID_TOKEN",
                              "message": "인증 토큰이 만료되었습니다.",
                              "status": 401,
                              "path": "/api/auth/login",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """))),
            @ApiResponse(responseCode = "403", description = "Firebase에서 이메일이 검증되지 않은 계정",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "EMAIL_NOT_VERIFIED",
                              "message": "검증되지 않은 이메일입니다.",
                              "status": 403,
                              "path": "/api/auth/login",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    MemberResponse login(@Parameter(hidden = true) AuthUser authUser, LoginRequest request);
}
