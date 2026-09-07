package uhsuhjupjup.backend.subscription.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import uhsuhjupjup.backend.subscription.ui.dto.UnsubscribeResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "수신거부", description = """
        알림 메일의 수신거부 링크가 걸리는 API

        ### 인증

        - 로그인이 필요 없습니다. 메일마다 고유한 토큰으로 본인을 식별합니다.

        ### 두 엔드포인트의 차이

        - 같은 경로에 GET과 POST가 있으며 호출 주체가 다릅니다. 각 엔드포인트 설명을 참고합니다.
        """)
public interface UnsubscribeControllerApi {

    @Operation(
            summary = "수신거부 (사용자가 링크를 클릭)",
            description = """
                    ### 호출 주체

                    - 사람이 브라우저로 여는 경로입니다. 메일 본문의 "수신거부" 링크가 이 주소를 가리킵니다.
                    - 프론트엔드가 fetch로 호출할 일은 없습니다.

                    ### 동작

                    - 구독을 해지한 뒤 프론트의 완료 안내 페이지로 302 리다이렉트합니다.
                    - 결과 안내는 리다이렉트된 페이지가 담당합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "해지 후 완료 페이지로 리다이렉트. `Location` 헤더를 따라간다", content = @Content)
    })
    ResponseEntity<Void> unsubscribe(
            @Parameter(description = "메일에 담긴 수신거부 토큰", required = true) String token);

    @Operation(
            summary = "원클릭 수신거부 (메일 클라이언트가 자동 호출)",
            description = """
                    ### 호출 주체

                    - 메일 클라이언트가 기계적으로 호출하는 경로입니다. RFC 8058 `List-Unsubscribe-Post` 규격에 대응합니다.
                    - Gmail 등에서 메일 상단의 "구독 취소" 버튼을 누르면, 사용자가 페이지를 열지 않아도 메일 클라이언트가 직접 POST합니다.

                    ### 응답

                    - 리다이렉트가 아니라 JSON을 반환합니다.
                    - 사람이 사용하는 화면에서는 같은 경로의 GET을 사용해야 합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해지 완료",
                    content = @Content(schema = @Schema(implementation = UnsubscribeResponse.class))),
            @ApiResponse(responseCode = "404", description = "토큰이 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "INVALID_UNSUBSCRIBE_TOKEN",
                              "message": "유효하지 않은 수신거부 링크입니다.",
                              "status": 404,
                              "path": "/api/unsubscribe",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    UnsubscribeResponse unsubscribeOneClick(
            @Parameter(description = "메일에 담긴 수신거부 토큰", required = true) String token);
}
