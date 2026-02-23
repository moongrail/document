package ru.itq.fun.document.dto.registry;

import ru.itq.fun.document.enums.DocumentStatus;

public record ConcurrentApproveResponse(
        Long documentId,
        int totalAttempts,
        int successCount,
        int conflictCount,
        int errorCount,
        int notFoundCount,
        DocumentStatus finalStatus
) {
}
