package ru.itq.fun.document.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.itq.fun.document.dto.CreateDocumentRequest;
import ru.itq.fun.document.dto.DocumentResponse;
import ru.itq.fun.document.dto.DocumentSearchRequest;
import ru.itq.fun.document.dto.DocumentWithHistoryResponse;
import ru.itq.fun.document.dto.registry.ApproveDocumentResponse;
import ru.itq.fun.document.dto.registry.ConcurrentApproveResponse;
import ru.itq.fun.document.dto.registry.SubmitDocumentsRequest;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;

import java.util.List;

public interface DocumentService {
    void create(CreateDocumentRequest request);

    DocumentWithHistoryResponse getDocumentWithHistory(Long id);

    Page<DocumentResponse> getPageDocuments( List<Long> ids, Pageable pageable);

    List<SubmitResultResponse> batchSubmitDocuments(SubmitDocumentsRequest request);

    List<ApproveDocumentResponse> batchApproveDocuments(List<Long> ids, String initiator, String comment);

    ConcurrentApproveResponse spamConcurrentApprove(Long documentId, int threads, int attempts, String initiator);

    Page<DocumentResponse> searchDocuments(DocumentSearchRequest request, Pageable pageable);

    void batchCreate(List<CreateDocumentRequest> requests);
}
