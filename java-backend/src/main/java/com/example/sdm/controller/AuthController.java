package com.example.sdm.controller;

import com.example.sdm.dto.LoginRequest;
import com.example.sdm.dto.SignupRequest;
import com.example.sdm.dto.TokenResponse;
import com.example.sdm.entity.User;
import com.example.sdm.service.AuthService;
import com.example.sdm.dto.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "0. Authentication API", description = "JWT 회원가입 및 로그인 서비스")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "[0-1] 회원가입", description = "신규 회원 정보를 등록하고 데이터베이스에 사용자를 저장합니다.")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
        User user = authService.signup(request);
        SignupResponse response = new SignupResponse(user.getEmail(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "[0-2] 로그인", description = "비밀번호 일치 여부를 검증하고 유효한 JWT Access Token을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
