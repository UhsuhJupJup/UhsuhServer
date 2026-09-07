package uhsuhjupjup.backend.emailsubscription.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.emailsubscription.ui.dto.AdminEmailSubscriberResponse;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "관리자 - 구독자", description = "구독자 현황을 조회하는 관리자 API")
public interface AdminEmailSubscriptionControllerApi {

    @Operation(
            summary = "구독자 목록 조회",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 조회 결과

                    - 구독자 전체를 반환합니다. 회원 구독자와 비회원 이메일 구독자가 함께 나오며 `recipientType`으로 구분합니다.
                    - `verified`가 `false`면 확인 메일 링크를 아직 누르지 않은 상태이므로 메일이 발송되지 않습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminEmailSubscriberResponse.class)))),
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
                                      "path": "/api/admin/email-subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    List<AdminEmailSubscriberResponse> list(@Parameter(hidden = true) Member admin);
}
