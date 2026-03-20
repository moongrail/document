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

import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentServiceImpl implements DocumentService {
    private static final Integer BATCH_SIZE = 50;

    private final DocumentDao documentDao;
    private final DocumentMapper mapper;
    private final DocumentBatchService documentBatchService;
    private final Executor virtualThreadExecutor;

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

        List<CompletableFuture<ApproveStatusResponse>> futures = IntStream.range(0, attempts)
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> documentBatchService.approveDocument(documentId, initiator, "concurrent test"),
                        virtualThreadExecutor
                ))
                .toList();

        CompletableFuture<ApproveStatusResponse>[] futuresArray = futures.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futuresArray).join();

        Map<ApproveStatusResponse, Long> counts = futures.stream()
                .map(CompletableFuture::join)
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
                request.status().name().toLowerCase(),
                request.author(),
                request.dateFrom() != null ? Timestamp.from(request.dateFrom().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()) : null,
                request.dateTo() != null ? Timestamp.from(request.dateTo().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant()) : null,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort())
        ).map(mapper::toDocumentResponse);
    }

    @Override
    @LogExecutionTime(step = "batchCreate")
    public void batchCreate(List<CreateDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<Document> documents = requests.stream()
                .map(mapper::toCreateDocument)
                .peek(doc -> doc.setStatus(DocumentStatus.DRAFT))
                .toList();

        documentDao.saveAll(documents);
    }
}