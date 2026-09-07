package uhsuhjupjup.backend.subscription.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.subscription.ui.dto.SubscriptionUpdateRequest;
import uhsuhjupjup.backend.subscription.ui.dto.SubscriptionsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "구독", description = """
        내가 어떤 키워드를 구독하는지 관리하는 API

        ### 알림 발송 기준

        - 알림은 키워드 구독에만 발송됩니다.
        - 토픽 구독은 저장되지만 메일 발송 대상이 아닙니다. 구독 화면에서 키워드를 펼쳐 고르게 하는 보조 수단으로만 사용합니다.
        """)
public interface SubscriptionControllerApi {

    @Operation(
            summary = "내 구독 목록 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 내가 구독 중인 토픽과 키워드를 반환합니다.
                    - 아무것도 구독하지 않았으면 두 배열 모두 빈 배열입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SubscriptionsResponse.class))),
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
                                      "path": "/api/subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    SubscriptionsResponse mySubscriptions(@Parameter(hidden = true) Member member);

    @Operation(
            summary = "구독 전체 교체",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 교체 방식

                    - 구독을 통째로 덮어씁니다. 부분 추가나 삭제가 아니라 전체 교체입니다.
                    - 보낸 목록에 없는 기존 구독은 해지됩니다.
                    - 화면에서는 현재 구독 목록을 불러온 뒤, 사용자가 변경한 최종 상태 전체를 보내야 합니다.

                    ### 요청 값

                    - 키워드 하나를 추가하려면 기존 키워드 전부와 새 키워드를 함께 보냅니다.
                    - 전부 해지하려면 빈 배열을 보내거나 `DELETE /api/subscriptions`를 사용합니다.
                    - `topicIds`와 `keywordIds`는 각각 생략할 수 있으며, 생략하면 빈 목록으로 간주합니다.

                    ### 응답

                    - 교체된 최종 상태를 그대로 반환하므로 별도 재조회가 필요 없습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교체 완료. 교체된 최종 상태를 반환한다",
                    content = @Content(schema = @Schema(implementation = SubscriptionsResponse.class))),
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
                                      "path": "/api/subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 토픽 또는 키워드 ID가 섞여 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "KEYWORD_NOT_FOUND",
                              "message": "키워드를 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/subscriptions",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    SubscriptionsResponse replace(@Parameter(hidden = true) Member member, SubscriptionUpdateRequest request);

    @Operation(
            summary = "구독 전체 해지",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 토픽과 키워드 구독을 모두 지웁니다.
                    - 성공하면 본문 없이 204를 반환합니다.
                    - 구독이 없는 상태에서 호출해도 오류가 아닙니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해지 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void unsubscribeAll(@Parameter(hidden = true) Member member);
}
