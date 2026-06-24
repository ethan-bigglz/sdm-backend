package com.example.sdm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 요청 정보")
public record SignupRequest(
    @Schema(description = "이메일 주소 (로그인 계정)", example = "user@example.com")
    String email,

    @Schema(description = "비밀번호 (8자 이상)", example = "password123")
    String password,

    @Schema(description = "사용자 이름", example = "홍길동")
    String username,

    @Schema(description = "회원 권한 (user, admin)", example = "user")
    String role
) {
    @JsonCreator
    public SignupRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("username") String username,
        @JsonProperty("role") String role
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty.");
        }
        this.email = email;
        this.password = password;
        this.username = username;
        this.role = (role == null || role.isBlank()) ? "user" : role;
    }
}
