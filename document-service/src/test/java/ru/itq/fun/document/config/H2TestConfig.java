package ru.itq.fun.document.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.itq.fun.document.dao.ApproveRegistryDao;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
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
    public DocumentBatchService documentBatchService(DocumentDao documentDao,
                                                     ApproveRegistryDao approveRegistryDao) {
        return new H2DocumentBatchService(documentDao, approveRegistryDao);
    }

    public static class H2DocumentBatchService implements DocumentBatchService {

        private final DocumentDao documentDao;
        private final ApproveRegistryDao approveRegistryDao;

        public H2DocumentBatchService(DocumentDao documentDao,
                                      ApproveRegistryDao approveRegistryDao) {
            this.documentDao = documentDao;
            this.approveRegistryDao = approveRegistryDao;
        }

        @Override
        public List<SubmitResultResponse> batchSubmitDocuments(long[] ids, String initiator, String comment) {
            return Arrays.stream(ids)
                    .mapToObj(id -> {
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
                        documentDao.insertHistory(id, initiator, "SUBMIT", comment);
                        return new SubmitResultResponse(id, "SUCCESS");
                    })
                    .toList();
        }

        @Override
        public ApproveStatusResponse approveDocument(Long id, String initiator, String comment) {
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

            approveRegistryDao.save(ru.itq.fun.document.entity.ApproveRegistry.builder()
                    .documentId(id)
                    .approveBy(initiator)
                    .approveAt(Instant.now())
                    .build());

            return ApproveStatusResponse.SUCCESS;
        }
    }
}
