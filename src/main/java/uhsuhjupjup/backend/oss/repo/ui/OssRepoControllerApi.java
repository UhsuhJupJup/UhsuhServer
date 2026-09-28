package uhsuhjupjup.backend.oss.repo.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.common.exception.ErrorResponse;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoCursor;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoSort;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoPageResponse;

@Tag(name = "오픈소스 - 레포", description = "오픈소스 레포 카탈로그를 탐색하는 API")
public interface OssRepoControllerApi {

    @Operation(
            summary = "레포 탐색",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 대상

                    - 카탈로그에 담긴 레포 중 정지되지 않은 레포만 반환합니다.
                    - 카테고리가 없는 레포도 나옵니다. 이때 `categories`는 빈 배열입니다.
                    - `categories`는 카테고리 목록 조회(`GET /api/oss/categories`)와 같은 순서입니다.

                    ### 필터

                    - 모든 필터는 선택이며, 여러 개를 함께 주면 AND로 좁혀집니다.
                    - `category`는 카테고리 코드 하나입니다. 카테고리 목록 조회가 돌려준 `code`와 대소문자까지 같아야 합니다.
                    - `language`는 GitHub 주 언어가 같은 레포를 찾습니다. 칼럼 정렬 규칙(utf8mb4_unicode_ci)을 따라 대소문자와 악센트를 가리지 않습니다. `C++`, `C#`처럼 URL에서 뜻이 있는 글자는 인코딩해서 보냅니다(`C%2B%2B`, `C%23`).
                    - `q`는 레포 이름(`owner/name`)이나 설명에 그 문자열이 들어 있는 레포를 찾습니다. 칼럼 정렬 규칙(utf8mb4_unicode_ci)을 따라 대소문자와 악센트를 가리지 않습니다(`cafe`로 `café`가 걸립니다). `%`와 `_`도 글자 그대로 찾습니다.
                    - 필터 값의 앞뒤 공백은 잘라 내고, 비어 있으면 그 필터는 쓰지 않습니다.

                    ### 정렬

                    - `stars`(기본)는 스타가 많은 순입니다. 스타 수가 같으면 레포 ID가 큰 순으로 순서를 고정합니다.
                    - `name`은 레포 이름(`owner/name`)의 오름차순입니다. 칼럼 정렬 규칙(utf8mb4_unicode_ci)을 따라 대소문자와 악센트를 가리지 않습니다.
                    - 값은 소문자 `stars`, `name` 두 가지뿐입니다. 빼거나 비우면 `stars`입니다.

                    ### 페이지 넘기기

                    - 한 번에 최대 `size`개를 반환하고, 마지막 페이지는 그보다 적을 수 있습니다. 기본 20개, 최대 50개입니다. 1보다 작으면 20, 50보다 크면 50으로 맞춥니다.
                    - 다음 페이지가 있으면 `nextCursor`에 커서를 담습니다. 다음 요청의 `cursor`에 그대로 넣고, 필터와 `sort`는 같은 값으로 보냅니다.
                    - 마지막 페이지면 `nextCursor`는 `null`입니다.
                    - 커서는 서버가 만든 문자열입니다. 풀어 보거나 고쳐 쓰지 않고 그대로 돌려보냅니다.
                    - 넘기는 동안 스타 수나 이름이 바뀐 레포는 빠지거나 두 번 나올 수 있고, 새로 담긴 레포는 빠질 수 있습니다.
                    - 전체 개수는 반환하지 않습니다.

                    ### 에러

                    - `sort`가 `stars`, `name`이 아니거나 `cursor`를 해석할 수 없으면 `VALIDATION_ERROR`로 400을 반환하고, `fieldErrors`에 파라미터 이름을 담습니다.
                    - `size`가 숫자가 아니면 `VALIDATION_ERROR`로 400을 반환합니다. int 범위를 넘는 값(예: `3000000000`)도 50으로 맞추지 않고 같은 400을 반환하며, `fieldErrors`의 이유는 `숫자여야 합니다.`입니다.
                    - 커서를 받은 정렬과 요청의 `sort`가 다르면 `VALIDATION_ERROR`로 400을 반환합니다. `sort`를 빼면 `stars`로 봅니다.
                    - 없는 카테고리 코드면 `OSS_CATEGORY_NOT_FOUND`로 400을 반환합니다. 대소문자만 달라도 없는 코드입니다.
                    - 요청 값(`VALIDATION_ERROR`), 카테고리(`OSS_CATEGORY_NOT_FOUND`) 순으로 확인하고, 먼저 걸린 것 하나만 반환합니다.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 결과가 없으면 `items`가 빈 배열이고 `nextCursor`는 null이다.",
                    content = @Content(
                            schema = @Schema(implementation = OssRepoPageResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
                                          "id": 10,
                                          "fullName": "spring-projects/spring-boot",
                                          "description": "Spring Boot helps you to create Spring-powered, production-grade applications and services with absolute minimum fuss.",
                                          "primaryLanguage": "Java",
                                          "stars": 80000,
                                          "categories": [
                                            {
                                              "code": "backend",
                                              "nameKo": "백엔드와 API",
                                              "nameEn": "Backend & APIs"
                                            },
                                            {
                                              "code": "devtools",
                                              "nameKo": "개발 도구",
                                              "nameEn": "Developer Tools"
                                            }
                                          ]
                                        }
                                      ],
                                      "nextCursor": "U1RBUlM6MTA6ODAwMDA"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 정렬, 커서, 크기, 커서와 다른 정렬, 또는 없는 카테고리 코드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "잘못된 정렬", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos",
                                      "timestamp": "2026-09-28T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "sort",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "잘못된 커서", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos",
                                      "timestamp": "2026-09-28T14:20:11.482",
                                      "fieldErrors": [
                                        {
                                          "field": "cursor",
                                          "reason": "형식이 올바르지 않습니다."
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "커서와 다른 정렬", value = """
                                    {
                                      "code": "VALIDATION_ERROR",
                                      "message": "요청 값이 올바르지 않습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos",
                                      "timestamp": "2026-09-28T14:20:11.482"
                                    }
                                    """),
                            @ExampleObject(name = "없는 카테고리 코드", value = """
                                    {
                                      "code": "OSS_CATEGORY_NOT_FOUND",
                                      "message": "없는 카테고리 코드가 있습니다.",
                                      "status": 400,
                                      "path": "/api/oss/repos",
                                      "timestamp": "2026-09-28T14:20:11.482"
                                    }
                                    """)}))
    })
    OssRepoPageResponse explore(
            @Parameter(description = "카테고리 코드. 대소문자까지 같아야 한다", example = "backend") String category,
            @Parameter(description = "GitHub 주 언어. 대소문자와 악센트는 가리지 않는다", example = "Java") String language,
            @Parameter(description = "레포 이름과 설명에서 찾을 문자열. 대소문자와 악센트는 가리지 않는다", example = "spring")
            String q,
            @Parameter(description = "정렬. 빼면 stars",
                    schema = @Schema(type = "string", allowableValues = {"stars", "name"}, defaultValue = "stars"))
            OssRepoSort sort,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 보내지 않는다", schema = @Schema(type = "string"))
            OssRepoCursor cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20") Integer size);
}
