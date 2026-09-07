package uhsuhjupjup.backend.pipeline.notification.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendLogResponse;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "관리자 - 발송 로그", description = "실제로 발송된 알림 메일의 영구 기록을 조회하는 관리자 API")
public interface AdminEmailSendLogControllerApi {

    @Operation(
            summary = "발송 로그 조회",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 조회 결과

                    - 발송에 성공한 메일을 최신순으로 반환합니다. 누구에게 언제 몇 건을 보냈는지 확인하는 용도입니다.
                    - 실패한 건은 여기 포함되지 않습니다. 실패는 `GET /api/admin/outbox`의 `failedEntries`에서 확인합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminEmailSendLogResponse.class)))),
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
                                      "path": "/api/admin/email-send-logs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    List<AdminEmailSendLogResponse> list(@Parameter(hidden = true) Member admin,
                                         @Parameter(description = "가져올 최대 건수. 기본 50", example = "50") int limit);
}
