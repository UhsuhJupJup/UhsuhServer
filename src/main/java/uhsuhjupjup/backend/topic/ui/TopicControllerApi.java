package uhsuhjupjup.backend.topic.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.topic.ui.dto.TopicDetailResponse;
import uhsuhjupjup.backend.topic.ui.dto.TopicResponse;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "토픽", description = "키워드를 묶는 상위 분류를 조회하는 API")
public interface TopicControllerApi {

    @Operation(
            summary = "토픽 목록 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 토픽의 id와 이름만 가볍게 반환합니다.
                    - 키워드까지 필요하면 `GET /api/topics/with-keywords`를 사용해야 합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TopicResponse.class))))
    })
    List<TopicResponse> list();

    @Operation(
            summary = "토픽 목록 조회, 키워드 포함",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 모든 토픽을 각 토픽의 키워드까지 한 번에 반환합니다.

                    ### 활용

                    - 구독 화면처럼 토픽을 펼쳐 키워드를 고르는 UI라면 이 엔드포인트를 한 번만 호출하면 됩니다.
                    - 토픽마다 `/api/topics/{id}`를 반복 호출하지 않아야 합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TopicDetailResponse.class))))
    })
    List<TopicDetailResponse> listWithKeywords();

    @Operation(
            summary = "토픽 상세 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 토픽 하나와 그 토픽에 속한 키워드 목록을 반환합니다.
                    - 키워드가 없는 토픽은 `keywords`가 빈 배열입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = TopicDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "해당 ID의 토픽이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "TOPIC_NOT_FOUND",
                              "message": "토픽을 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/topics/999",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    TopicDetailResponse detail(@Parameter(description = "토픽 ID", example = "1", required = true) Long id);
}
