package com.example.sdm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청 정보")
public record LoginRequest(
    @Schema(description = "이메일 주소", example = "user@example.com")
    String email,

    @Schema(description = "비밀번호", example = "password123")
    String password
) {
    @JsonCreator
    public LoginRequest(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        this.email = email;
        this.password = password;
    }
}
