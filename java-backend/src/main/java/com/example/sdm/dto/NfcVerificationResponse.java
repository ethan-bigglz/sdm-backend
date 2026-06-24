package com.example.sdm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "NFC 태그 검증 완료 응답 정보")
public record NfcVerificationResponse(
    @Schema(description = "NFC UID (Hex 문자열)", example = "04A1B2C3D4E5F6")
    String nfcUid,

    @Schema(description = "아이템 소유 상태 (unclaimed, claimed, pending_transfer)", example = "unclaimed")
    String status,

    @Schema(description = "스마트 컨트랙트 상의 고유 토큰 ID", example = "101")
    Integer nftTokenId,

    @Schema(description = "연관 상품 ID", example = "PROD_001")
    String productId,

    @Schema(description = "검증 상세 메시지", example = "정품 인증 성공")
    String message
) {}
