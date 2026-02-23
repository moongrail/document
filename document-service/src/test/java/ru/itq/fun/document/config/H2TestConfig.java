package ru.itq.fun.document.config;

import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.itq.fun.document.dao.ApproveRegistryDao;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.entity.ApproveRegistry;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.enums.DocumentStatus;
import ru.itq.fun.document.service.DocumentBatchService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@TestConfiguration
public class H2TestConfig {

    @Bean
    @Primary
    public DocumentBatchService h2DocumentBatchService(DocumentDao documentDao,
                                                       ApproveRegistryDao approveRegistryDao,
                                                       EntityManager entityManager) {
        return new H2CompatibleBatchService(documentDao, approveRegistryDao, entityManager);
    }

    public static class H2CompatibleBatchService implements DocumentBatchService {

        private final DocumentDao documentDao;
        private final ApproveRegistryDao approveRegistryDao;
        private final EntityManager entityManager;

        public H2CompatibleBatchService(DocumentDao documentDao,
                                        ApproveRegistryDao approveRegistryDao,
                                        EntityManager entityManager) {
            this.documentDao = documentDao;
            this.approveRegistryDao = approveRegistryDao;
            this.entityManager = entityManager;
        }

        @Override
        public List<SubmitResultResponse> batchSubmitDocuments(long[] ids, String initiator, String comment) {
            entityManager.flush();

            return Arrays.stream(ids)
                    .mapToObj(id -> {
                        entityManager.clear();
                        Optional<Document> opt = documentDao.findById(id);
                        if (opt.isEmpty()) {
                            return new SubmitResultResponse(id, "NOT_FOUND");
                        }
                        Document doc = opt.get();
                        if (doc.getStatus() != DocumentStatus.DRAFT) {
                            return new SubmitResultResponse(id, "CONFLICT");
                        }
                        doc.setStatus(DocumentStatus.SUBMITTED);
                        documentDao.save(doc);
                        entityManager.flush();
                        documentDao.insertHistory(id, initiator, "SUBMIT", comment);
                        return new SubmitResultResponse(id, "SUCCESS");
                    })
                    .toList();
        }

        @Override
        public ApproveStatusResponse approveDocument(Long id, String initiator, String comment) {
            entityManager.flush();
            entityManager.clear();

            Optional<Document> opt = documentDao.findById(id);
            if (opt.isEmpty()) {
                return ApproveStatusResponse.NOT_FOUND;
            }
            Document doc = opt.get();
            if (doc.getStatus() != DocumentStatus.SUBMITTED) {
                return ApproveStatusResponse.CONFLICT;
            }

            int updated = documentDao.submitToApproved(id);
            if (updated == 0) {
                return ApproveStatusResponse.CONFLICT;
            }

            documentDao.insertHistory(id, initiator, "APPROVE", comment);

            approveRegistryDao.save(ApproveRegistry.builder()
                    .documentId(id)
                    .approveBy(initiator)
                    .approveAt(Instant.now())
                    .build());

            entityManager.flush();

            return ApproveStatusResponse.SUCCESS;
        }
    }
}