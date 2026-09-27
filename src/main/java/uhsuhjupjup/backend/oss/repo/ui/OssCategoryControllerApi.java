package uhsuhjupjup.backend.oss.repo.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssCategoryResponse;

import java.util.List;

@Tag(name = "오픈소스 - 카테고리", description = "레포 탐색의 카테고리 필터와 온보딩의 관심 카테고리 선택에 쓰는 카테고리 사전을 조회하는 API")
public interface OssCategoryControllerApi {

    @Operation(
            summary = "카테고리 목록 조회",
            description = """
                    ### 인증

                    - 로그인 없이 호출할 수 있습니다.

                    ### 조회 결과

                    - 카테고리 사전 전체를 서버가 정한 노출 순서대로 반환합니다. 받은 순서 그대로 보여 주면 됩니다.
                    - 카테고리마다 `code`, 한국어 이름 `nameKo`, 영어 이름 `nameEn`을 함께 반환합니다.

                    ### 활용

                    - `code`는 카테고리를 구분하는 안정적인 식별자입니다. 카테고리를 가리킬 때는 이름이 아니라 `code`를 사용합니다.
                    - 화면에는 사용자가 설정한 언어에 맞춰 `nameKo`나 `nameEn`을 표시합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OssCategoryResponse.class))))
    })
    List<OssCategoryResponse> list();
}
