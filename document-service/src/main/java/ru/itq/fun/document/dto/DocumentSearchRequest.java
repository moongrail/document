package ru.itq.fun.document.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;
import ru.itq.fun.document.enums.DocumentStatus;

import java.time.LocalDate;

public record DocumentSearchRequest(
        DocumentStatus status,
        String author,
        @Past
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dateFrom,
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dateTo
) {
}
