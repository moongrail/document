package ru.itq.fun.document.dto.registry;


public enum ApproveStatusResponse {
    SUCCESS("Успех"),
    CONFLICT("Конфликт"),
    NOT_FOUND("Не найдено"),
    ERROR("Ошибка");

    private final String status;

    ApproveStatusResponse(String name) {
        this.status = name;
    }

    public String getName() {
        return status;
    }
}
