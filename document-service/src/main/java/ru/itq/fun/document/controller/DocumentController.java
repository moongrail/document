package ru.itq.fun.document.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.itq.fun.document.dto.CreateDocumentRequest;
import ru.itq.fun.document.dto.DocumentResponse;
import ru.itq.fun.document.dto.DocumentSearchRequest;
import ru.itq.fun.document.dto.DocumentWithHistoryResponse;
import ru.itq.fun.document.dto.registry.*;
import ru.itq.fun.document.service.DocumentService;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Valid
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody @Valid CreateDocumentRequest request) {
        documentService.create(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public DocumentWithHistoryResponse getDocumentWithHistory(@PathVariable("id") @Positive Long id) {
        return documentService.getDocumentWithHistory(id);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<DocumentResponse> getPageDocuments(@RequestParam(required = false) List<Long> ids,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                   Pageable pageable) {
        return documentService.getPageDocuments(ids, pageable);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    public List<SubmitResultResponse> submitDocuments(
            @RequestBody @Valid SubmitDocumentsRequest request) {
        return documentService.batchSubmitDocuments(request);
    }

    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    public List<ApproveDocumentResponse> approveDocuments(
            @RequestBody @Valid ApproveDocumentsRequest request) {
        return documentService.batchApproveDocuments(request.ids(), request.initiator(), request.comment());
    }

    @PostMapping("/approve/concurrent-spam")
    @ResponseStatus(HttpStatus.OK)
    public ConcurrentApproveResponse spamConcurrentApprove(
            @RequestParam Long documentId,
            @RequestParam @Positive @Max(50) int threads,
            @RequestParam @Positive @Max(100) int attempts,
            @RequestParam @NotEmpty String initiator) {
        return documentService.spamConcurrentApprove(documentId, threads, attempts, initiator);
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    public Page<DocumentResponse> searchDocuments(
            @Valid DocumentSearchRequest request,
            @PageableDefault(size = 20, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return documentService.searchDocuments(request, pageable);
    }

    @PostMapping("/create/batch")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    public void batchCreate(@RequestBody @Valid List<CreateDocumentRequest> requests) {
        documentService.batchCreate(requests);
    }

}

