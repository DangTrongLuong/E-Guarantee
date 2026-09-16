package com.example.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(name = "LoginResponse", description = "Thông tin token khi đăng nhập thành công")
public class LoginResponse {
    @Schema(description = "Access token dùng cho request API", example = "eyJhbGciOiJIUzUxMiJ9...")
    private String accessToken;
    @Schema(description = "Refresh token dùng để cấp lại access token", example = "eyJhbGciOiJIUzUxMiJ9...")
    private String refreshToken;
}
