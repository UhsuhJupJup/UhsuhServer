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
import uhsuhjupjup.backend.pipeline.notification.ui.dto.AdminOutboxResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "관리자 - 발송 아웃박스", description = """
        메일 발송 대기열을 조회하고 실패 건을 재시도하는 관리자 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 발송 구조

        - 발송은 곧바로 보내지 않고 아웃박스에 적재한 뒤 워커가 꺼내 보내는 구조입니다.
        - 따라서 발송이 실패해도 기록이 남고 재시도할 수 있습니다.
        """)
public interface AdminOutboxControllerApi {

    @Operation(
            summary = "아웃박스 현황 조회",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 조회 결과

                    - 대기(`pending`), 발송 완료(`sent`), 실패(`failed`) 건수와 실패 목록을 함께 반환합니다.
                    - `failedEntries[].attempts`는 재시도 횟수, `lastError`는 마지막 실패 사유입니다.

                    ### 운영 지표

                    - 대시보드 경고 지표로는 `failed`를 사용합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminOutboxResponse.class))),
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
                                      "path": "/api/admin/outbox/91",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    AdminOutboxResponse summary(@Parameter(hidden = true) Member admin,
                                @Parameter(description = "함께 반환할 실패 건 최대 개수. 기본 50", example = "50") int failedLimit);

    @Operation(
            summary = "실패 건 재시도",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 실패한 발송 건을 다시 대기 상태로 되돌립니다. 워커가 다음 주기에 집어 발송을 재시도합니다.
                    - 발송 자체는 즉시 일어나지 않습니다.

                    ### 확인 방법

                    - 호출 후 `GET /api/admin/outbox`로 상태 변화를 확인합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재큐 완료", content = @Content),
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
                                      "path": "/api/admin/outbox/91",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 아웃박스 항목이 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "NOT_FOUND",
                                      "message": "리소스를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/outbox/91",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void requeue(@Parameter(hidden = true) Member admin,
                 @Parameter(description = "아웃박스 항목 ID", example = "91", required = true) Long id);
}
