package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.LoginRequest;
import com.example.ecommerce.dto.request.UserRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.LoginResponse;
import com.example.ecommerce.dto.response.UserResponse;
import com.example.ecommerce.service.AuthenticationService;
import com.example.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo mới người dùng với role mặc định MARKER")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody UserRequest request){
        UserResponse response = userService.create(request);
        log.info("Register success for username={}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Register success", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Xác thực username/password và trả về access token, refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request){
        LoginResponse response = authenticationService.login(request);
        log.info("Login success for username={}", request.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Login success", response));
    }

    @PostMapping(value = "/logout")
    @Operation(summary = "Đăng xuất", description = "Vô hiệu hóa access token và refresh token hiện tại")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String bearerToken) throws ParseException {
        String token = bearerToken.replace("Bearer ", "");
        authenticationService.logout(token);
        log.info("Logout success");
        return ResponseEntity.ok(ApiResponse.success("Logout success", null));
    }

}
