package com.example.sdm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "P2P 소유권 양도 요청")
public record TransferRequest(
    @Schema(description = "양도하려는 실물 상품 ID", example = "1")
    Integer itemId,

    @Schema(description = "수령할 사용자의 Username", example = "recipientUser")
    String recipientUsername
) {
    @JsonCreator
    public TransferRequest(
        @JsonProperty("itemId") Integer itemId,
        @JsonProperty("recipientUsername") String recipientUsername
    ) {
        if (itemId == null) {
            throw new IllegalArgumentException("Item ID cannot be null.");
        }
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new IllegalArgumentException("Recipient username cannot be null or empty.");
        }
        this.itemId = itemId;
        this.recipientUsername = recipientUsername;
    }
}
