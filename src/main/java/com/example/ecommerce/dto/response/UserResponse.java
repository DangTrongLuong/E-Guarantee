package com.example.ecommerce.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(name = "UserResponse", description = "Thông tin người dùng")
public class UserResponse {
    @Schema(description = "ID người dùng", example = "1")
    private Long id;
}
