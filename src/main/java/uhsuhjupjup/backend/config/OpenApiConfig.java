package uhsuhjupjup.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.common.auth.AdminMember;
import uhsuhjupjup.backend.common.auth.LoginAuthUser;
import uhsuhjupjup.backend.common.auth.LoginMember;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "firebaseIdToken";

    static {
        SpringDocUtils.getConfig()
                .addAnnotationsToIgnore(LoginMember.class, LoginAuthUser.class, AdminMember.class);
    }

    @Bean
    public OpenAPI uhsuhjupjupOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("어서줍줍 API")
                        .description("기술 블로그의 새 글을 키워드로 주워다주는 서비스의 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Firebase 로그인 후 받은 ID 토큰. 토큰이 없으면 공개 엔드포인트만 동작한다.");
    }
}
