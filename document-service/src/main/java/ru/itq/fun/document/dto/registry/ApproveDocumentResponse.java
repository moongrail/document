package ru.itq.fun.document.dto.registry;

public record ApproveDocumentResponse(
        Long id,
        ApproveStatusResponse status
) {}
