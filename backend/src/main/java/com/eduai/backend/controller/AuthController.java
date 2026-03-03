package com.eduai.backend.controller;

import com.eduai.backend.model.LoginRequest;
import com.eduai.backend.model.LoginResponse;
import com.eduai.backend.model.RegisterRequest;
import com.eduai.backend.model.RegisterResponse;
import com.eduai.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        boolean created = userService.register(request);
        if (!created) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RegisterResponse(false, "User already exists"));
        }
        return ResponseEntity.ok(new RegisterResponse(true, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        boolean ok = userService.login(request);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, "Invalid credentials", request.username()));
        }
        return ResponseEntity.ok(new LoginResponse(true, "Login successful", request.username()));
    }
}
