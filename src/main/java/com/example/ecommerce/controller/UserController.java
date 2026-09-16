package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.UserRequest;
import com.example.ecommerce.dto.response.UserResponse;
import com.example.ecommerce.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/users")
    public UserResponse create(@Valid @RequestBody UserRequest request){
        return userService.create(request);
    }

    @GetMapping("/users")
    public List<UserResponse> getAll(){
        return userService.getAll();
    }


}
