package uhsuhjupjup.backend.learningnote.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteGraphResponse;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteRecommendationResponse;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteRequest;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteResponse;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import uhsuhjupjup.backend.common.exception.ErrorResponse;

@Tag(name = "학습 노트", description = """
        공부한 내용을 적어두면 관련 글을 추천해주는 API

        ### 인증

        - 모든 엔드포인트가 로그인을 요구하며 본인 노트만 다룰 수 있습니다.
        - 다른 사람의 노트에 접근하면 403이 아니라 404를 반환합니다. 노트 존재 여부를 노출하지 않기 위해서입니다.
        """)
public interface NoteControllerApi {

    @Operation(
            summary = "노트 작성",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 다룰 수 있습니다.

                    ### 동작

                    - 노트를 생성하고 201과 함께 생성된 노트를 반환합니다.
                    - 생성 시점에는 아직 분석 전이라 `keywords`가 빈 배열이고 `analyzedAt`이 `null`입니다.
                    - 키워드는 `GET /api/notes/{id}/recommendations`를 처음 호출할 때 채워집니다.

                    ### 입력 제한

                    - `title`은 1자 이상 200자 이하이며 공백만으로는 안 됩니다.
                    - `content`는 1자 이상 10000자 이하이며 공백만으로는 안 됩니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "작성 완료",
                    content = @Content(schema = @Schema(implementation = NoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "제목이나 본문이 비었거나 길이를 초과함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "VALIDATION_ERROR",
                              "message": "요청 값이 올바르지 않습니다.",
                              "status": 400,
                              "path": "/api/notes",
                              "timestamp": "2026-09-04T14:20:11.482",
                              "fieldErrors": [
                                { "field": "title", "reason": "공백일 수 없습니다" }
                              ]
                            }
                            """))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    NoteResponse create(@Parameter(hidden = true) Member member, NoteRequest request);

    @Operation(
            summary = "내 노트 목록 조회",
            description = """
                    ### 인증

                    - 로그인이 필요합니다.

                    ### 조회 결과

                    - 내가 쓴 노트를 최신순으로 반환합니다.
                    - 목록에서도 `content` 전문이 담기므로 미리보기는 클라이언트에서 잘라 사용해야 합니다.
                    - 노트가 없으면 빈 배열입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 노트가 없으면 빈 배열",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NoteResponse.class)))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    List<NoteResponse> list(@Parameter(hidden = true) Member member);

    @Operation(summary = "노트 상세 조회",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 조회할 수 있습니다.
                    - 다른 사람의 노트는 404로 응답합니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NoteResponse.class))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "노트가 없거나 내 노트가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = """
                            {
                              "code": "NOTE_NOT_FOUND",
                              "message": "노트를 찾을 수 없습니다.",
                              "status": 404,
                              "path": "/api/notes/999",
                              "timestamp": "2026-09-04T14:20:11.482"
                            }
                            """)))
    })
    NoteResponse get(@Parameter(hidden = true) Member member, @Parameter(description = "노트 ID", example = "12", required = true) Long id);

    @Operation(
            summary = "노트 수정",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 다룰 수 있습니다.

                    ### 동작

                    - 제목과 본문을 덮어씁니다.
                    - 본문이 바뀌면 분석 결과가 초기화되어 `analyzedAt`이 다시 `null`이 되고 `keywords`가 비워집니다.
                    - 다음 추천 요청 때 다시 분석합니다.

                    ### 입력 제한

                    - 작성과 동일합니다. `title` 1~200자, `content` 1~10000자입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료. 본문이 바뀌었으면 `analyzedAt`이 다시 null이 된다",
                    content = @Content(schema = @Schema(implementation = NoteResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 값 오류",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "노트가 없거나 내 노트가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "NOTE_NOT_FOUND",
                                      "message": "노트를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    NoteResponse update(@Parameter(hidden = true) Member member,
                        @Parameter(description = "노트 ID", example = "12", required = true) Long id,
                        NoteRequest request);

    @Operation(summary = "노트 삭제",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 삭제할 수 있습니다.

                    ### 동작

                    - 노트를 삭제하고 본문 없이 204를 반환합니다.
                    - 노트에 딸린 분석 결과도 함께 삭제됩니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료. 응답 본문 없음", content = @Content),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "노트가 없거나 내 노트가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "NOTE_NOT_FOUND",
                                      "message": "노트를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    void delete(@Parameter(hidden = true) Member member, @Parameter(description = "노트 ID", example = "12", required = true) Long id);

    @Operation(
            summary = "노트 기반 글 추천",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 다룰 수 있습니다.

                    ### 응답 시간

                    - 아직 분석되지 않은 노트라면 이 요청 안에서 AI가 노트를 읽고 키워드를 추출하므로 수 초가 걸립니다.
                    - 두 번째 호출부터는 분석 결과를 재사용해 빠릅니다.
                    - 클라이언트는 첫 호출에 로딩 상태를 반드시 표시해야 합니다.

                    ### 응답 구성

                    - `keywords`는 노트에서 추출한 키워드입니다.
                    - `articles[].matchedKeywords`는 그 글이 추천된 이유입니다.
                    - 관련 키워드까지 확장해 점수를 매기므로, 노트에 없던 키워드가 `matchedKeywords`에 나올 수 있습니다.
                    - 걸리는 글이 없으면 `articles`가 빈 배열입니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 성공. 걸리는 글이 없으면 `articles`가 빈 배열",
                    content = @Content(schema = @Schema(implementation = NoteRecommendationResponse.class))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "노트가 없거나 내 노트가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "NOTE_NOT_FOUND",
                                      "message": "노트를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    NoteRecommendationResponse recommendations(@Parameter(hidden = true) Member member,
                                               @Parameter(description = "노트 ID", example = "12", required = true) Long id);

    @Operation(
            summary = "노트 지식 그래프 조회",
            description = """
                    ### 인증

                    - 로그인이 필요하며 본인 노트만 다룰 수 있습니다.

                    ### 노드 종류

                    - `nodes[].type`으로 구분합니다.
                    - `note` — 노트 자신입니다. `id`는 `note:{노트ID}` 형식입니다.
                    - `keyword` — 키워드입니다. `id`는 `kw:{키워드ID}`이며, `inNote`가 `true`면 노트에서 직접 추출된 키워드입니다.
                    - `article` — 추천된 글입니다. `id`는 `art:{글ID}`이며 `rank`가 추천 순위입니다.

                    ### 응답 시간

                    - 추천과 같은 분석을 사용하므로 첫 호출은 이 엔드포인트도 느릴 수 있습니다.
                    """,
            security = @SecurityRequirement(name = "firebaseIdToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = NoteGraphResponse.class))),
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
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "404",
                    description = "노트가 없거나 내 노트가 아님",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "NOTE_NOT_FOUND",
                                      "message": "노트를 찾을 수 없습니다.",
                                      "status": 404,
                                      "path": "/api/notes/12",
                                      "timestamp": "2026-09-07T14:20:11.482"
                                    }
                                    """)))
    })
    NoteGraphResponse graph(@Parameter(hidden = true) Member member,
                            @Parameter(description = "노트 ID", example = "12", required = true) Long id);
}
