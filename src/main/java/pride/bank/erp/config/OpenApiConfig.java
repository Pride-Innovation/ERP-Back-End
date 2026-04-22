/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Provide a valid JWT access token obtained from POST /api/v1/auth/login"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI erpOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pride Bank ERP API")
                        .description("Internal ERP system for Pride Bank Limited. " +
                                "All endpoints require a valid JWT bearer token except authentication routes.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Pride Bank IT Department")
                                .email("it@pridebank.co.ug"))
                        .license(new License()
                                .name("Proprietary – Pride Bank Limited")
                                .url("https://pridebank.co.ug")));
    }
}
