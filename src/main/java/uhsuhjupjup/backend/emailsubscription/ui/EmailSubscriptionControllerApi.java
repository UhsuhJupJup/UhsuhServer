package uhsuhjupjup.backend.emailsubscription.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import uhsuhjupjup.backend.emailsubscription.ui.dto.EmailSubscriptionRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManageLinkRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManageSubscriptionsRequest;
import uhsuhjupjup.backend.emailsubscription.ui.dto.ManagedSubscriptionsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "비회원 이메일 구독", description = """
        로그인 없이 이메일 주소만으로 구독하는 API

        ### 회원 구독과의 관계

        - 회원 구독(`/api/subscriptions`)과는 완전히 별개 경로입니다.
        - 인증은 토큰 대신 메일로 보낸 일회성 링크로 합니다. 따라서 이 태그의 엔드포인트는 모두 로그인이 필요 없습니다.
        """)
public interface EmailSubscriptionControllerApi {

    @Operation(
            summary = "이메일 구독 등록",
            description = """
                    ### 동작

                    - 이메일과 키워드를 받아 확인 메일을 발송합니다. 이 시점에는 아직 구독이 확정되지 않습니다.
                    - 사용자가 메일의 링크를 눌러야(`GET /confirm`) 실제 구독이 됩니다.
                    - 성공하면 본문 없이 202를 반환합니다. 202는 접수했다는 뜻이지 구독됐다는 뜻이 아닙니다.
                    - 화면에는 "메일함을 확인해 주세요"로 안내해야 합니다.

                    ### 이미 등록된 이메일

                    - 회원으로 가입된 이메일이면 `EMAIL_ALREADY_MEMBER`로 409를 반환합니다. 로그인 후 회원 구독을 쓰도록 유도합니다.
                    - 이미 확인까지 마친 구독자면 `EMAIL_ALREADY_SUBSCRIBED`로 409를 반환합니다. 관리 링크 요청으로 유도합니다.
                    - 확인 메일을 받고 아직 링크를 누르지 않은 상태라면 재등록이 허용됩니다. 이때는 키워드가 새로 보낸 값으로 교체되고 확인 메일이 다시 발송됩니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수 완료, 확인 메일 발송됨. 응답 본문 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "이미 회원이거나 이미 구독 완료된 이메일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "이미 회원", value = """
                                    {
                                      "code": "EMAIL_ALREADY_MEMBER",
                                      "message": "이미 가입된 이메일입니다. 로그인 후 구독해 주세요.",
                                      "status": 409,
                                      "path": "/api/email-subscriptions",
                                      "timestamp": "2026-09-04T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "이미 구독 중", value = """
                                    {
                                      "code": "EMAIL_ALREADY_SUBSCRIBED",
                                      "message": "이미 구독 중인 이메일입니다. 관리 링크로 변경해 주세요.",
                                      "status": 409,
                                      "path": "/api/email-subscriptions",
                                      "timestamp": "2026-09-04T14:20:11.482"
                                    }
                                    """)})),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 키워드 ID가 섞여 있음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "KEYWORD_NOT_FOUND",
                                      "message": "키워드를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/email-subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "이메일 형식이 잘못되었거나 키워드가 비어 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "VALIDATION_ERROR",
                              "message": "요청 값이 올바르지 않습니다.",
                              "status": 400,
                              "path": "/api/email-subscriptions",
                              "timestamp": "2026-09-04T14:20:11.482",
                              "fieldErrors": [
                                { "field": "email", "reason": "올바른 형식의 이메일 주소여야 합니다" }
                              ]
                            }
                            """)))
    })
    void register(EmailSubscriptionRequest request);

    @Operation(
            summary = "구독 확인 (메일 링크 진입점)",
            description = """
                    ### 호출 주체

                    - 확인 메일의 링크가 걸리는 주소입니다. 사용자가 브라우저로 직접 여는 경로이며, 프론트엔드가 fetch로 호출할 일은 없습니다.

                    ### 동작

                    - 토큰을 검증한 뒤 프론트 랜딩 페이지로 302 리다이렉트합니다.
                    - 성공과 실패 모두 리다이렉트로 처리되므로 결과 안내는 랜딩 페이지가 담당합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "프론트 랜딩으로 리다이렉트. `Location` 헤더를 따라간다", content = @Content)
    })
    ResponseEntity<Void> confirm(
            @Parameter(description = "확인 메일에 담긴 일회성 토큰", required = true) String token);

    @Operation(
            summary = "구독 관리 링크 요청",
            description = """
                    ### 동작

                    - 해당 이메일로 관리용 매직 링크를 발송합니다.

                    ### 계정 열거 방지

                    - 등록되지 않은 이메일이어도 동일하게 202를 반환합니다. 응답만으로 가입 여부를 알아낼 수 없게 하기 위해서입니다.
                    - 따라서 화면에서 "가입되지 않은 이메일입니다" 같은 안내를 해서는 안 됩니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수 완료. 응답 본문 없음. 이메일 존재 여부와 무관하게 동일", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/email-subscriptions/manage-link",
                                      "timestamp": "2026-09-07T14:20:11.482",
                                      "fieldErrors": [
                                        { "field": "email", "reason": "올바른 형식의 이메일 주소여야 합니다" }
                                      ]
                                    }
                                    """)))
    })
    void requestManageLink(ManageLinkRequest request);

    @Operation(
            summary = "구독 내역 조회 (관리 토큰)",
            description = """
                    ### 동작

                    - 관리 링크의 토큰으로 현재 구독 중인 키워드를 조회합니다. 관리 화면 진입 시 호출합니다.

                    ### 토큰

                    - 토큰은 만료되며 재사용 횟수에 제한이 있습니다.
                    - 만료되면 404를 반환하므로, 이때는 "링크를 다시 요청해 주세요" 안내와 함께 `POST /manage-link`로 유도합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ManagedSubscriptionsResponse.class))),
            @ApiResponse(responseCode = "404", description = "토큰이 유효하지 않거나 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "INVALID_MANAGE_TOKEN",
                              "message": "유효하지 않거나 만료된 관리 링크입니다.",
                              "status": 404,
                              "path": "/api/email-subscriptions/manage",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    ManagedSubscriptionsResponse getManaged(
            @Parameter(description = "관리 메일에 담긴 토큰", required = true) String token);

    @Operation(
            summary = "구독 키워드 교체 (관리 토큰)",
            description = """
                    ### 교체 방식

                    - 구독 키워드를 통째로 덮어씁니다. 회원 구독과 마찬가지로 부분 변경이 아니라 전체 교체입니다.
                    - 보낸 목록에 없는 키워드는 해지됩니다.

                    ### 요청 값

                    - `keywordIds`는 비어 있을 수 없습니다.
                    - 전부 해지하려면 메일의 수신거부 링크를 사용해야 합니다.
                    - 성공하면 본문 없이 204를 반환합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "교체 완료. 응답 본문 없음", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "키워드 목록이 비어 있음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/email-subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "토큰이 유효하지 않거나 만료됨",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "INVALID_MANAGE_TOKEN",
                                      "message": "유효하지 않거나 만료된 관리 링크입니다.",
                                      "status": 404,
                                      "path": "/api/email-subscriptions",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void updateManaged(@Parameter(description = "관리 메일에 담긴 토큰", required = true) String token,
                       ManageSubscriptionsRequest request);
}
