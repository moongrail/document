package ru.itq.fun.document.exception;

public class DocumentConflictException extends RuntimeException {
    public DocumentConflictException(String message) {
        super(message);
    }
}
