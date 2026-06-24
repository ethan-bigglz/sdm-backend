package com.example.sdm.dto;

import com.example.sdm.entity.ItemHistory;
import java.time.LocalDateTime;

public record ItemHistoryResponse(
    Integer id,
    String eventType,
    String fromUsername,
    String fromEmail,
    String toUsername,
    String toEmail,
    String txHash,
    LocalDateTime createdAt
) {
    public static ItemHistoryResponse from(ItemHistory history) {
        return new ItemHistoryResponse(
            history.getId(),
            history.getEventType(),
            history.getFromUser() != null ? history.getFromUser().getUsername() : null,
            history.getFromUser() != null ? history.getFromUser().getEmail() : null,
            history.getToUser() != null ? history.getToUser().getUsername() : null,
            history.getToUser() != null ? history.getToUser().getEmail() : null,
            history.getTxHash(),
            history.getCreatedAt()
        );
    }
}
