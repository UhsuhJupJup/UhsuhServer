package uhsuhjupjup.backend.learningnote.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteGraphResponse;
import uhsuhjupjup.backend.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;

@Tag(name = "지식 그래프", description = "키워드가 서로 어떻게 얽혀 있는지를 노드와 간선으로 반환하는 API")
public interface GraphControllerApi {

    @Operation(
            summary = "전역 지식 그래프 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 그래프 구성

                    - 수집된 글 전체를 대상으로 만든 키워드 그래프입니다.
                    - `nodes[].id`는 `kw:{키워드ID}` 형식입니다. 숫자만 필요하면 접두사를 떼고 사용합니다.
                    - `nodes[].weight`는 그 키워드가 등장한 글 수입니다. 노드 크기에 사용합니다.
                    - `edges[].weight`는 두 키워드가 함께 등장한 글 수입니다. 선 굵기에 사용합니다.
                    - `edges[].source`와 `target`은 `nodes[].id`를 가리킵니다.

                    ### 갱신 주기

                    - 매일 06시 수집 배치 이후 갱신됩니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NoteGraphResponse.class)))
    })
    NoteGraphResponse global();

    @Operation(
            summary = "내 구독 키워드 그래프 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 그래프 구성

                    - 내가 구독한 키워드만 잘라낸 부분 그래프입니다. 전역 그래프와 응답 형태는 같습니다.
                    - 구독했지만 아직 글이 하나도 없는 키워드는 간선이 없는 외톨이 노드로 나옵니다.
                    - 아무것도 구독하지 않았으면 `nodes`와 `edges`가 모두 빈 배열입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NoteGraphResponse.class))),
            @ApiResponse(responseCode = "401", description = "토큰이 없거나 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "UNAUTHORIZED",
                              "message": "인증이 필요합니다.",
                              "status": 401,
                              "path": "/api/graph/mine",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    NoteGraphResponse mine(@Parameter(hidden = true) Member member);
}
