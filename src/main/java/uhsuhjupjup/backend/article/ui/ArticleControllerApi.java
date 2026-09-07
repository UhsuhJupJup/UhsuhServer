package uhsuhjupjup.backend.article.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.article.ui.dto.ArticleDetailResponse;
import uhsuhjupjup.backend.article.ui.dto.ArticlePageResponse;

import java.util.List;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "글", description = "수집된 기술 블로그 글을 조회하는 API")
public interface ArticleControllerApi {

    @Operation(
            summary = "글 목록 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 필터

                    - 모든 필터는 선택이며, 여러 개를 함께 주면 AND로 좁혀집니다.
                    - `topicIds`와 `keywordIds`는 값을 여러 번 지정할 수 있고, 같은 파라미터 안에서는 OR로 묶입니다.
                    - `?topicIds=1&topicIds=2&blogId=7`은 (토픽 1 또는 2) 그리고 블로그 7을 뜻합니다.

                    ### 조회 결과

                    - 수집 시각 기준 최신순으로 반환합니다.
                    - 결과가 없으면 `content`가 빈 배열이고 `totalElements`는 0입니다.
                    - 저작권 정책에 따라 글 본문은 응답에 포함하지 않습니다. 원문은 `url`로 이동해야 합니다.
                    """)
    @ApiResponses(@ApiResponse(
            responseCode = "200",
            description = "조회 성공. 결과가 없으면 `content`가 빈 배열이다.",
            content = @Content(schema = @Schema(implementation = ArticlePageResponse.class))))
    ArticlePageResponse list(
            @Parameter(description = "토픽 ID 목록. 반복 지정하면 OR", example = "1") List<Long> topicIds,
            @Parameter(description = "키워드 ID 목록. 반복 지정하면 OR", example = "42") List<Long> keywordIds,
            @Parameter(description = "블로그 ID", example = "7") Long blogId,
            @Parameter(description = "제목 검색어", example = "Redis") String q,
            @Parameter(description = "0부터 시작하는 페이지 번호. 기본 0", example = "0") Integer page,
            @Parameter(description = "페이지 크기. 기본 20", example = "20") Integer size);

    @Operation(
            summary = "글 상세 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 매칭 근거

                    - 목록과 달리 키워드마다 `matchedVia`를 함께 반환합니다.
                    - `AI` — Claude가 제목과 본문을 읽고 분류한 경우입니다.
                    - `SUBSTRING` — 제목에 키워드 문자열이 그대로 포함된 경우입니다.

                    ### 본문

                    - 저작권 정책에 따라 본문은 저장하지도 반환하지도 않습니다.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ArticleDetailResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 글이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "ARTICLE_NOT_FOUND",
                              "message": "요청한 글을 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/articles/99999",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    ArticleDetailResponse detail(@Parameter(description = "글 ID", example = "421", required = true) Long id);
}
