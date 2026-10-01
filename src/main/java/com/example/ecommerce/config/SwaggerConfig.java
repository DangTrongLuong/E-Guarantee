package com.example.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

        @Bean
        public OpenAPI openAPI() {
                final String securitySchemaName = "bearerAuth";
                return new OpenAPI()
                                .info(new Info()
                                                .title("E-Commerce Order Management System")
                                                .description("Ứng dụng cho phép quản lý Customer, Product, Order và Order Item. Người dùng có thể tạo đơn hàng, thêm sản phẩm vào đơn, cập nhật trạng thái đơn hàng và xem lịch sử đơn hàng")
                                                .version("1.0.0"))
                                // .servers(List.of(
                                // new Server()
                                // .url("https://e-guarantee.datn-nextgen-suggest.site")
                                // .description("Production"))) // Production
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemaName))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemaName, new SecurityScheme()
                                                                .name(securitySchemaName)
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")));

        }
}
