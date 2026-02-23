package ru.itq.fun.document.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.itq.fun.document.dto.CreateDocumentRequest;
import ru.itq.fun.document.dto.DocumentResponse;
import ru.itq.fun.document.dto.DocumentWithHistoryResponse;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.entity.DocumentHistory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "author", source = "author")
    @Mapping(target = "title", source = "title")
    Document toCreateDocument(CreateDocumentRequest request);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "number", source = "number")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "status", source = "status")
    DocumentResponse toDocumentResponse(Document document);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "number", source = "number")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "mapTime")
    @Mapping(target = "modifiedAt", source = "modifiedAt", qualifiedByName = "mapTime")
    @Mapping(target = "history", source = "document.history", qualifiedByName = "mapHistory")
    DocumentWithHistoryResponse toDocumentWithHistoryResponse(Document document);

    @Named("mapHistory")
    default List<DocumentHistory> mapHistory(List<DocumentHistory> documentHistories) {
        if (documentHistories == null) {
            return new ArrayList<>();
        }
        return documentHistories;
    }

    @Named("mapTime")
    default LocalDateTime mapTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.of("Europe/Moscow"));
    }
}
