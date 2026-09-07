package uhsuhjupjup.backend.blog.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.blog.ui.dto.AdminBlogResponse;
import uhsuhjupjup.backend.blog.ui.dto.BlogCreateRequest;
import uhsuhjupjup.backend.blog.ui.dto.BlogUpdateRequest;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "관리자 - 블로그", description = """
        수집 대상 블로그를 관리하는 API

        ### 권한

        - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

        ### 공개 API와의 차이

        - 공개 `GET /api/blogs`는 활성 블로그만 반환하지만, 여기서는 비활성까지 전부 반환합니다.
        - RSS 주소는 관리자 API에서만 노출합니다.
        """)
public interface AdminBlogControllerApi {

    @Operation(summary = "블로그 전체 조회", description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다.

                    ### 조회 결과

                    - 비활성 블로그를 포함한 전체 목록과 RSS 주소를 반환합니다.
                    - `active`가 `false`면 수집이 멈춘 블로그입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminBlogResponse.class)))),
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
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    List<AdminBlogResponse> list(@Parameter(hidden = true) Member admin);

    @Operation(
            summary = "블로그 추가",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 수집 대상을 새로 등록하고 201과 함께 생성된 블로그를 반환합니다.

                    ### 요청 값

                    - `domain`은 중복될 수 없습니다. 같은 블로그를 두 번 등록하는 것을 막기 위해서이며, 중복이면 409를 반환합니다.
                    - `domain`은 호스트만 적습니다. Medium 계열은 `medium.com/publication` 형식을 사용합니다.
                    - `logoUrl`은 선택입니다.

                    ### 등록 후 영향

                    - 등록 즉시 다음 수집 배치(매일 06시)에서 해당 피드 전체를 가져옵니다.
                    - 과거 글이 많은 블로그를 등록하면 수백 건이 한 번에 유입되고, 그 글들이 새 글로 간주되어 알림 대상이 됩니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료",
                    content = @Content(schema = @Schema(implementation = AdminBlogResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "name, domain, rssUrl 중 빈 값이 있음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
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
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(responseCode = "409", description = "같은 도메인이 이미 등록되어 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "BLOG_ALREADY_EXISTS",
                              "message": "이미 등록된 블로그 도메인입니다.",
                              "status": 409,
                              "path": "/api/admin/blogs",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    AdminBlogResponse add(@Parameter(hidden = true) Member admin, BlogCreateRequest request);

    @Operation(
            summary = "블로그 수정",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다.

                    ### 변경 가능 항목

                    - 이름, RSS 주소, 로고를 변경합니다.
                    - `domain`은 변경할 수 없습니다. 블로그를 식별하는 값이기 때문입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료",
                    content = @Content(schema = @Schema(implementation = AdminBlogResponse.class))),
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
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 블로그가 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "BLOG_NOT_FOUND",
                                      "message": "블로그를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    AdminBlogResponse update(@Parameter(hidden = true) Member admin,
                             @Parameter(description = "블로그 ID", example = "7", required = true) Long id,
                             BlogUpdateRequest request);

    @Operation(
            summary = "블로그 비활성화",
            description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다. 일반 회원이 호출하면 403을 반환합니다.

                    ### 동작

                    - 수집을 멈춥니다. 성공하면 본문 없이 204를 반환합니다.
                    - 이미 수집된 글은 그대로 남고 앞으로 새 글만 가져오지 않습니다.
                    - 공개 목록(`GET /api/blogs`)에서도 사라집니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "비활성화 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 블로그가 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "BLOG_NOT_FOUND",
                                      "message": "블로그를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void deactivate(@Parameter(hidden = true) Member admin,
                    @Parameter(description = "블로그 ID", example = "7", required = true) Long id);

    @Operation(summary = "블로그 활성화", description = """
                    ### 권한

                    - ADMIN 권한이 필요합니다.

                    ### 동작

                    - 수집을 다시 시작하고 본문 없이 204를 반환합니다.
                    - 다음 배치부터 새 글을 가져옵니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "활성화 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 블로그가 없음",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "BLOG_NOT_FOUND",
                                      "message": "블로그를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/admin/blogs",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void activate(@Parameter(hidden = true) Member admin,
                  @Parameter(description = "블로그 ID", example = "7", required = true) Long id);
}
