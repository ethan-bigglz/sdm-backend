package com.example.sdm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 성공 후 발급되는 JWT 토큰 정보")
public record TokenResponse(
    @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,

    @Schema(description = "토큰 타입", example = "Bearer")
    String tokenType,

    @Schema(description = "토큰 만료 시간 (초 단위)", example = "3600")
    long expiresIn
) {
    public TokenResponse(String accessToken, long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}
