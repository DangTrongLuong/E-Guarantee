package com.example.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(){
        final String securitySchemaName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Order Management System")
                        .description("Ứng dụng cho phép quản lý Customer, Product, Order và Order Item. Người dùng có thể tạo đơn hàng, thêm sản phẩm vào đơn, cập nhật trạng thái đơn hàng và xem lịch sử đơn hàng")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemaName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemaName, new SecurityScheme()
                                .name(securitySchemaName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                        )
                );

    }
}
