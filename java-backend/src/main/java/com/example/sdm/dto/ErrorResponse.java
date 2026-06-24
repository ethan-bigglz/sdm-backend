package com.example.sdm.dto;

import java.time.LocalDateTime;

/**
 * API 표준 에러 응답 포맷 (Java Record 사용)
 */
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {
    // 편의 생성자
    public ErrorResponse(int status, String error, String message, String path) {
        this(LocalDateTime.now(), status, error, message, path);
    }
}
