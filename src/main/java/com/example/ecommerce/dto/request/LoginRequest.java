package com.example.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    @Schema(description = "Tên đăng nhập", example = "john_doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Mật khẩu", example = "StrongPass123")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    private String password;

}
