package ru.itq.fun.document.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itq.fun.document.config.WorkerProperties;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.registry.ApproveDocumentResponse;
import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.enums.DocumentStatus;
import ru.itq.fun.document.service.DocumentService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApproveWorker {

    private final DocumentDao documentDao;
    private final DocumentService documentService;
    private final WorkerProperties props;

    @Scheduled(cron = "${worker.approve.cron:-}")
    public void process() {
        log.info("ApproveWorker started");

        Pageable pageable = PageRequest.of(0, props.batchSize());

        Page<Long> page;
        do {
            page = documentDao.findIdsByStatus(DocumentStatus.SUBMITTED, pageable);
            if (page.isEmpty()) break;

            List<Long> ids = page.getContent();
            log.info("ApproveWorker processing {} documents", ids.size());

            try {
                List<ApproveDocumentResponse> results =
                        documentService.batchApproveDocuments(ids, props.initiator(), "Approve Worker");

                long success = results.stream()
                        .filter(r -> r.status() == ApproveStatusResponse.SUCCESS)
                        .count();
                log.info("ApproveWorker batch done: {}/{} success", success, ids.size());
            } catch (Exception e) {
                log.error("ApproveWorker batch failed: {}", e.getMessage());
            }

        } while (page.hasNext());

        log.info("ApproveWorker finished");
    }
}