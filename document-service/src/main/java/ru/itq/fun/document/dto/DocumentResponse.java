package ru.itq.fun.document.dto;

import ru.itq.fun.document.enums.DocumentStatus;

public record DocumentResponse(

        Long id,
        String author,
        String number,
        String title,
        DocumentStatus status
) {
}
