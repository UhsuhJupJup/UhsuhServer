package uhsuhjupjup.backend.blog.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.blog.ui.dto.BlogDetailResponse;
import uhsuhjupjup.backend.blog.ui.dto.BlogResponse;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "블로그", description = "글을 수집해 오는 기술 블로그를 조회하는 API")
public interface BlogControllerApi {

    @Operation(
            summary = "블로그 목록 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 범위

                    - 수집 대상으로 활성화된 블로그만 반환합니다. 비활성 블로그는 목록에 나오지 않습니다.
                    - 비활성 블로그까지 보려면 관리자 API(`GET /api/admin/blogs`)를 사용해야 합니다.

                    ### 활용

                    - 글 목록을 블로그로 좁힐 때(`GET /api/articles?blogId=`) 여기서 받은 `id`를 사용합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BlogResponse.class))))
    })
    List<BlogResponse> list();

    @Operation(
            summary = "블로그 상세 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 활성 여부와 무관하게 조회할 수 있습니다.
                    - `logoUrl`은 등록되지 않았으면 `null`입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BlogDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "해당 ID의 블로그가 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "BLOG_NOT_FOUND",
                              "message": "블로그를 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/blogs/999",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    BlogDetailResponse detail(@Parameter(description = "블로그 ID", example = "7", required = true) Long id);
}
