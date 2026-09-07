package uhsuhjupjup.backend.member.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.ui.dto.ConsentResponse;
import uhsuhjupjup.backend.member.ui.dto.MemberResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "회원", description = "내 계정 정보와 메일 수신 동의, 세션을 관리하는 API")
public interface MemberControllerApi {

    @Operation(
            summary = "내 정보 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 로그인한 회원 본인의 정보를 반환합니다.
                    - `consentAt`이 `null`이면 아직 메일 수신에 동의하지 않은 상태입니다.
                    - 동의 전에는 구독을 설정해도 알림 메일이 발송되지 않으므로, 구독 화면에서 동의를 유도해야 합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MemberResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "UNAUTHORIZED",
                                      "message": "인증이 필요합니다.",
                                      "status": 401,
                                      "path": "/api/members/me",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    MemberResponse me(@Parameter(hidden = true) Member member);

    @Operation(
            summary = "메일 수신 동의",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 알림 메일 수신에 동의하고 동의 시각을 기록합니다.
                    - 이미 동의한 상태에서 다시 호출해도 안전합니다.

                    ### 주의

                    - 동의하지 않으면 구독을 설정해도 메일이 발송되지 않습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동의 완료",
                    content = @Content(schema = @Schema(implementation = ConsentResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "UNAUTHORIZED",
                                      "message": "인증이 필요합니다.",
                                      "status": 401,
                                      "path": "/api/members/me",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    ConsentResponse consent(@Parameter(hidden = true) Member member);

    @Operation(
            summary = "모든 기기에서 로그아웃",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 발급된 세션을 전부 무효화합니다. 다른 기기에 남아 있는 로그인까지 끊깁니다.
                    - 성공하면 본문 없이 204를 반환합니다.

                    ### 클라이언트 처리

                    - 호출 후 Firebase 로그아웃도 함께 수행해야 합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 완료. 응답 본문 없음", content = @Content),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "UNAUTHORIZED",
                                      "message": "인증이 필요합니다.",
                                      "status": 401,
                                      "path": "/api/members/me",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void logoutAll(@Parameter(hidden = true) Member member);
}
