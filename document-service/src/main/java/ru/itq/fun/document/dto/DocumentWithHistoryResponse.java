package ru.itq.fun.document.dto;

import ru.itq.fun.document.entity.DocumentHistory;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentWithHistoryResponse(
        Long id,
        String author,
        String number,
        String title,
        String status,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        List<DocumentHistory> history
) {
}
