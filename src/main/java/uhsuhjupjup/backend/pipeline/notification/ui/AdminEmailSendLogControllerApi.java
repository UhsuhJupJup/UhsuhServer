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
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendDailyResponse;
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminEmailSendLogResponse;

import java.time.LocalDate;
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
                    - `date`(YYYY-MM-DD)를 넘기면 그 날짜에 발송된 전체 건을 최신순으로 반환하며 `limit`은 무시됩니다.
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
                                         @Parameter(description = "가져올 최대 건수. 기본 50", example = "50") int limit,
                                         @Parameter(description = "발송 날짜(YYYY-MM-DD). 지정하면 그 날 전체를 반환하고 limit은 무시", example = "2026-09-06") LocalDate date);

    @Operation(
            summary = "발송 로그 날짜별 요약",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 조회 결과

                    - 발송에 성공한 메일을 날짜별로 묶어 최신 날짜부터 반환합니다.
                    - `sentAt`은 그 날의 첫 발송 시각(버스트 시작)입니다.
                    - `total`은 그 날 발송 건수, `memberCount`는 회원 수신, `subscriberCount`는 이메일 구독자 수신 건수입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminEmailSendDailyResponse.class)))),
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
                                      "path": "/api/admin/email-send-logs/daily",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    List<AdminEmailSendDailyResponse> daily(@Parameter(hidden = true) Member admin);
}
