package ru.itq.fun.document.dto;

import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Length;

public record CreateDocumentRequest(
        @Length(max = 255, min = 1)
        @NotEmpty
        String author,
        @Length(max = 255, min = 1)
        @NotEmpty
        String title
) {
}
