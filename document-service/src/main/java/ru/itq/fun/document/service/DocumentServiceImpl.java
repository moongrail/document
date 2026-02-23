package ru.itq.fun.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itq.fun.document.aop.LogExecutionTime;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.CreateDocumentRequest;
import ru.itq.fun.document.dto.DocumentResponse;
import ru.itq.fun.document.dto.DocumentSearchRequest;
import ru.itq.fun.document.dto.DocumentWithHistoryResponse;
import ru.itq.fun.document.dto.registry.ApproveDocumentResponse;
import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.dto.registry.ConcurrentApproveResponse;
import ru.itq.fun.document.dto.registry.SubmitDocumentsRequest;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.enums.DocumentStatus;
import ru.itq.fun.document.exception.ApproveRegistryException;
import ru.itq.fun.document.exception.DocumentConflictException;
import ru.itq.fun.document.exception.DocumentNotFoundException;
import ru.itq.fun.document.mapper.DocumentMapper;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentServiceImpl implements DocumentService {
    private static final Integer BATCH_SIZE = 50;

    private final DocumentDao documentDao;
    private final DocumentMapper mapper;
    private final DocumentBatchService documentBatchService;

    @Override
    @LogExecutionTime(step = "create document")
    public void create(CreateDocumentRequest request) {
        if (documentDao.existsByAuthorAndTitle(request.author(), request.title())) {
            throw new DocumentConflictException("Already exist...");
        }

        Document doc = mapper.toCreateDocument(request);
        doc.setStatus(DocumentStatus.DRAFT);
        documentDao.save(doc);
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecutionTime(step = "getDocumentWithHistory")
    public DocumentWithHistoryResponse getDocumentWithHistory(Long id) {

        Document document = documentDao.findDocument(id).orElseThrow(() -> new DocumentNotFoundException(id));

        return mapper.toDocumentWithHistoryResponse(document);
    }

    @Transactional(readOnly = true)
    @LogExecutionTime(step = "getPageDocuments")
    public Page<DocumentResponse> getPageDocuments(List<Long> ids, Pageable pageable) {
        if (ids == null || ids.isEmpty()) {
            return Page.empty(pageable);
        }

        return documentDao.findDocumentsByIds(ids, pageable)
                .map(mapper::toDocumentResponse);
    }

    @Override
    @LogExecutionTime(step = "INIT batchSubmitDocuments")
    public List<SubmitResultResponse> batchSubmitDocuments(SubmitDocumentsRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return List.of();
        }

        List<SubmitResultResponse> results = new ArrayList<>();
        int total = request.ids().size();
        log.info("batchSubmitDocuments started: total={}", total);

        for (int i = 0; i < request.ids().size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, request.ids().size());
            long[] batch = request.ids().subList(i, endIndex)
                    .stream()
                    .mapToLong(Long::longValue)
                    .toArray();
            log.info("batchSubmitDocuments processing: {}-{} of {}", i + 1, endIndex, total);
            try {
                results.addAll(documentBatchService.batchSubmitDocuments(batch, request.initiator(), request.comment()));
                log.info("batchSubmitDocuments processed: {}/{} done", endIndex, total);

            } catch (Exception e) {
                log.error("Batch failed for ids {} - {}: {}", i, endIndex, e.getMessage());
                throw new ApproveRegistryException("Batch failed");
            }
        }

        return results;
    }

    @Override
    @LogExecutionTime(step = "INIT batchApproveDocuments")
    public List<ApproveDocumentResponse> batchApproveDocuments(
            List<Long> ids, String initiator, String comment) {

        List<ApproveDocumentResponse> results = new ArrayList<>();

        int total = ids.size();
        log.info("batchApproveDocuments started: total={}", total);

        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, ids.size());
            List<Long> batch = ids.subList(i, endIndex);

            log.info("batchApproveDocuments processing batch: {}-{} of {}", i + 1, endIndex, total);
            for (Long id : batch) {
                try {
                    ApproveStatusResponse status = documentBatchService.approveDocument(id, initiator, comment);
                    results.add(new ApproveDocumentResponse(id, status));
                } catch (Exception e) {
                    log.error("Failed to registry document {}: {}", id, e.getMessage());
                    results.add(new ApproveDocumentResponse(id, ApproveStatusResponse.ERROR));
                }
            }
            log.info("batchApproveDocuments processed: {}/{} done, remaining={}", endIndex, total, total - endIndex);
        }

        return results;
    }

    @Override
    @LogExecutionTime(step = "spamConcurrentApprove")
    public ConcurrentApproveResponse spamConcurrentApprove(
            Long documentId, int threads, int attempts, String initiator) {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<ApproveStatusResponse>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() ->
                    documentBatchService.approveDocument(documentId, initiator, "concurrent test")
            ));
        }

        executor.close();

        Map<ApproveStatusResponse, Long> counts = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        log.error("Attempt failed: {}", e.getMessage());
                        return ApproveStatusResponse.ERROR;
                    }
                })
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        Collectors.counting()
                ));

        DocumentStatus finalStatus = documentDao.findById(documentId)
                .map(Document::getStatus)
                .orElseThrow();

        return new ConcurrentApproveResponse(
                documentId,
                attempts,
                counts.getOrDefault(ApproveStatusResponse.SUCCESS, 0L).intValue(),
                counts.getOrDefault(ApproveStatusResponse.CONFLICT, 0L).intValue(),
                counts.getOrDefault(ApproveStatusResponse.ERROR, 0L).intValue(),
                counts.getOrDefault(ApproveStatusResponse.NOT_FOUND, 0L).intValue(),
                finalStatus
        );
    }

    @Override
    @Transactional(readOnly = true)
    @LogExecutionTime(step = "searchDocuments")
    public Page<DocumentResponse> searchDocuments(DocumentSearchRequest request, Pageable pageable) {
        return documentDao.searchDocuments(
                request.status() != null ? request.status().name() : null,
                request.author(),
                request.dateFrom() != null ? request.dateFrom().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                request.dateTo() != null ? request.dateTo().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC) : null,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
        ).map(mapper::toDocumentResponse);
    }

    @Override
    @LogExecutionTime(step = "batchCreate")
    public void batchCreate(List<CreateDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        List<Document> documents = requests.stream()
                .map(r -> {
                    Document doc = mapper.toCreateDocument(r);
                    doc.setStatus(DocumentStatus.DRAFT);
                    return doc;
                })
                .toList();

        documentDao.saveAll(documents);
    }
}