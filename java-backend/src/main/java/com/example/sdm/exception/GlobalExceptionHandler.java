package com.example.sdm.exception;

import com.example.sdm.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 애플리케이션 예외 처리기.
 * 발생한 예외를 잡아 표준화된 ErrorResponse(Record) 형태로 응답합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 상품 중복 보유 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(DuplicateItemOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateItemOwnership(DuplicateItemOwnershipException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }

    /**
     * NFC 아이템 코드 불일치 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(ItemCodeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleItemCodeMismatch(ItemCodeMismatchException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }

    /**
     * 등록되지 않은 NFC 태그 예외 처리 (404 Not Found)
     */
    @ExceptionHandler(TagNotRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleTagNotRegistered(TagNotRegisteredException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }

    /**
     * DTO validation (@Valid) 실패 예외 처리 (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "[%s] %s".formatted(error.getField(), error.getDefaultMessage()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");

        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                errorMessage,
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }

    /**
     * 경로 혹은 리소스가 존재하지 않을 때 예외 처리 (404 Not Found)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }

    /**
     * 시스템 전반적인 일반 예외 처리 (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse response = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, status);
    }
}
