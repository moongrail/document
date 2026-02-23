package ru.itq.fun.document.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itq.fun.document.aop.LogExecutionTime;
import ru.itq.fun.document.dao.ApproveRegistryDao;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.entity.ApproveRegistry;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.enums.DocumentHistoryOperation;
import ru.itq.fun.document.enums.DocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentBatchServiceImpl implements DocumentBatchService {

    private final DocumentDao documentDao;
    private final ApproveRegistryDao approveRegistryDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @LogExecutionTime(step = "BATCH batchSubmitDocuments")
    public List<SubmitResultResponse> batchSubmitDocuments(long[] batch, String initiator, String comment) {
        return documentDao.batchSubmittedDocuments(batch, initiator, comment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApproveStatusResponse approveDocument(Long id, String initiator, String comment) {
        Optional<Document> documentOpt = documentDao.findByIdForUpdate(id);

        if (documentOpt.isEmpty()) {
            return documentDao.existsById(id)
                    ? ApproveStatusResponse.CONFLICT
                    : ApproveStatusResponse.NOT_FOUND;
        }

        Document document = documentOpt.get();

        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            return ApproveStatusResponse.CONFLICT;
        }

        int updated = documentDao.submitToApproved(id);
        if (updated == 0) {
            return ApproveStatusResponse.CONFLICT;
        }

        documentDao.insertHistory(id, initiator, DocumentHistoryOperation.APPROVE.toString(), comment);

        ApproveRegistry registry = ApproveRegistry.builder()
                .documentId(id)
                .approveBy(initiator)
                .approveAt(Instant.now())
                .build();

        approveRegistryDao.save(registry);

        return ApproveStatusResponse.SUCCESS;
    }
}
