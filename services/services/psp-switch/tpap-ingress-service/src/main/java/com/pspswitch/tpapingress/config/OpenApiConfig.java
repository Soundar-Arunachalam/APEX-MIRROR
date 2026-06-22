package com.pspswitch.tpapingress.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI customOpenAPI() {
                // Define security schemes for the required headers
                SecurityScheme tpapIdScheme = new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-TPAP-ID")
                                .description("TPAP ID (e.g., phonepe)");

                // Add them to components
                Components components = new Components()
                                .addSecuritySchemes("TpapId", tpapIdScheme);

                // Define a global security requirement requiring the header
                SecurityRequirement securityRequirement = new SecurityRequirement()
                                .addList("TpapId");

                return new OpenAPI()
                                .info(new Info()
                                                .title("TPAP Ingress Service API")
                                                .version("1.0")
                                                .description("API Documentation for TPAP Ingress Service"))
                                .components(components)
                                .addSecurityItem(securityRequirement);
        }
}
