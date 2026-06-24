package com.example.sdm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개별 실물 상품(NFC-NFT 매핑) 등록 요청")
public record ItemRegisterRequest(
    @Schema(description = "연관 상품 템플릿 ID", example = "PROD_001")
    String productId,

    @Schema(description = "NFC 태그 UID (7바이트 Hex 문자열)", example = "04A1B2C3D4E5F6")
    String nfcUid
) {
    @JsonCreator
    public ItemRegisterRequest(
        @JsonProperty("productId") String productId,
        @JsonProperty("nfcUid") String nfcUid
    ) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty.");
        }
        if (nfcUid == null || nfcUid.isBlank() || nfcUid.length() != 14) {
            throw new IllegalArgumentException("NFC UID must be a valid 14-character hexadecimal string.");
        }
        this.productId = productId;
        this.nfcUid = nfcUid;
    }
}
