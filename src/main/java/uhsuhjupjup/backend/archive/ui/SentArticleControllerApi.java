package uhsuhjupjup.backend.archive.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.archive.ui.dto.SentArticlesResponse;
import uhsuhjupjup.backend.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "보관함 - 알림받은 글", description = "메일로 발송된 글의 이력을 조회하는 API")
public interface SentArticleControllerApi {

    @Operation(
            summary = "알림받은 글 목록 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 내게 메일로 발송된 글을 발송 시각(`sentAt`) 기준 최신순으로 반환합니다.
                    - 발송 이력이 없으면 `content`가 빈 배열입니다.

                    ### 즐겨찾기와의 차이

                    - 즐겨찾기(`/api/me/bookmarks`)는 내가 직접 담은 글이고, 이쪽은 시스템이 보낸 기록입니다.
                    - "메일을 못 봤는데 무엇이 왔었는지" 확인하는 화면을 상정했습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 발송 이력이 없으면 빈 배열",
                    content = @Content(schema = @Schema(implementation = SentArticlesResponse.class))),
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
                                      "path": "/api/me/notifications",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    SentArticlesResponse mySentArticles(@Parameter(hidden = true) Member member);
}
