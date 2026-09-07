package uhsuhjupjup.backend.archive.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.archive.ui.dto.BookmarkedArticlesResponse;
import uhsuhjupjup.backend.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "보관함 - 즐겨찾기", description = "관심 있는 글을 따로 담아두는 API")
public interface BookmarkControllerApi {

    @Operation(
            summary = "즐겨찾기 목록 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 내가 담아둔 글을 담은 시각(`bookmarkedAt`) 기준 최신순으로 반환합니다.
                    - 담아둔 글이 없으면 `content`가 빈 배열입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BookmarkedArticlesResponse.class))),
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
                                      "path": "/api/me/bookmarks/421",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    BookmarkedArticlesResponse myBookmarks(@Parameter(hidden = true) Member member);

    @Operation(
            summary = "즐겨찾기 추가",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 글을 즐겨찾기에 담습니다. 성공하면 본문 없이 201을 반환합니다.
                    - 이미 담은 글을 다시 담아도 오류가 아닙니다.

                    ### 클라이언트 처리

                    - 토글 UI라면 담긴 상태를 클라이언트가 유지하고, 추가는 POST, 해제는 DELETE로 나눠 호출합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/me/bookmarks/421",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 글이 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "ARTICLE_NOT_FOUND",
                                      "message": "요청한 글을 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/me/bookmarks/421",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void addBookmark(@Parameter(hidden = true) Member member,
                     @Parameter(description = "글 ID", example = "421", required = true) Long articleId);

    @Operation(
            summary = "즐겨찾기 해제",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 동작

                    - 담아둔 글을 뺍니다. 성공하면 본문 없이 204를 반환합니다.
                    - 담겨 있지 않은 글을 빼도 오류가 아닙니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/me/bookmarks/421",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void removeBookmark(@Parameter(hidden = true) Member member,
                        @Parameter(description = "글 ID", example = "421", required = true) Long articleId);
}
