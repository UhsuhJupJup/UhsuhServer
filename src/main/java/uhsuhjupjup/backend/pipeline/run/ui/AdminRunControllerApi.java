package uhsuhjupjup.backend.pipeline.run.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.ui.dto.PipelineRunPageResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "관리자 - 파이프라인", description = """
        수집, 매칭, 발송 배치의 실행 이력을 조회하고 발송을 수동 실행하는 관리자 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 정규 일정

        - 매일 06시에 수집과 매칭, 08시에 발송이 실행됩니다. 기준 시간대는 Asia/Seoul입니다.
        """)
public interface AdminRunControllerApi {

    @Operation(
            summary = "파이프라인 실행 이력 조회",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 조회 결과

                    - 배치 실행 결과를 최신순으로 반환하며 `kind`로 종류를 구분합니다.
                    - `INGEST` — 06시 수집과 매칭입니다. `collectedNew`(새 글), `matchedArticles`(분류된 글), `tagsCreated`(붙은 키워드)를 봅니다.
                    - `NOTIFICATION` — 08시 발송입니다. `membersNotified`와 `notificationsRecorded`를 봅니다.

                    ### 이상 징후

                    - `collectedNew`가 계속 0이면 수집이 멈춘 것이므로 블로그 RSS 상태를 의심해야 합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PipelineRunPageResponse.class))),
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
                                      "path": "/api/admin/runs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    PipelineRunPageResponse list(@Parameter(hidden = true) Member admin,
                                 @Parameter(description = "페이지 번호. 0부터 시작, 기본 0", example = "0") int page,
                                 @Parameter(description = "페이지 크기. 기본 20", example = "20") int size);

    @Operation(
            summary = "알림 발송 즉시 실행",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 08시를 기다리지 않고 발송 배치를 즉시 실행합니다.
                    - 실행 결과를 동기로 반환하므로 응답이 오래 걸릴 수 있습니다.
                    - 발송 대상 기준은 정규 배치와 같습니다.

                    ### 멱등성

                    - 이미 발송한 글은 다시 보내지 않습니다. 연달아 호출해도 같은 사람에게 중복 발송되지 않습니다.
                    - 두 번째 호출은 대개 0건으로 끝납니다.

                    ### 다중 인스턴스

                    - 분산락이 걸려 있어 다른 인스턴스가 실행 중이면 건너뛰고 0건을 반환합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실행 완료. 발송 결과 요약을 반환한다",
                    content = @Content(schema = @Schema(implementation = NotificationResult.class))),
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
                                      "path": "/api/admin/runs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    NotificationResult triggerNotification(@Parameter(hidden = true) Member admin);
}
