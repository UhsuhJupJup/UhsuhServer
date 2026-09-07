package uhsuhjupjup.backend.keyword.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.keyword.ui.dto.KeywordDetailResponse;
import uhsuhjupjup.backend.keyword.ui.dto.KeywordResponse;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "키워드", description = "구독 단위가 되는 키워드를 조회하는 API. 알림은 키워드 기준으로만 발송됩니다.")
public interface KeywordControllerApi {

    @Operation(
            summary = "키워드 검색, 목록 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 필터

                    - `q`와 `topicId`는 모두 선택입니다. 아무것도 주지 않으면 전체 키워드를 반환합니다.
                    - `q`는 이름 부분 일치로 검색합니다.
                    - 두 파라미터를 함께 주면 AND로 좁혀집니다.

                    ### 활용

                    - 구독 화면의 키워드 자동완성에 사용하는 것을 상정했습니다.
                    - 일치하는 키워드가 없으면 빈 배열을 반환합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 일치하는 키워드가 없으면 빈 배열",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = KeywordResponse.class))))
    })
    List<KeywordResponse> list(
            @Parameter(description = "이름 검색어. 부분 일치", example = "redis") String q,
            @Parameter(description = "이 토픽에 속한 키워드만", example = "1") Long topicId);

    @Operation(
            summary = "키워드 상세 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 키워드 하나와 그 키워드가 속한 토픽 목록을 함께 반환합니다.
                    - 하나의 키워드가 여러 토픽에 동시에 속할 수 있습니다.
                    - 어느 토픽에도 속하지 않은 키워드는 `topics`가 빈 배열입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = KeywordDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "해당 ID의 키워드가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "KEYWORD_NOT_FOUND",
                              "message": "키워드를 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/keywords/999",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    KeywordDetailResponse detail(@Parameter(description = "키워드 ID", example = "42", required = true) Long id);
}
