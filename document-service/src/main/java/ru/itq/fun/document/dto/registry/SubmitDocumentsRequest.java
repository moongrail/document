package ru.itq.fun.document.dto.registry;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

import java.util.List;

public record SubmitDocumentsRequest(
        @NotEmpty @Size(max = 1000) List<Long> ids,
        @Length(max = 255, min = 2) @NotEmpty String initiator,
        @Size(max = 255) String comment
) {
}
