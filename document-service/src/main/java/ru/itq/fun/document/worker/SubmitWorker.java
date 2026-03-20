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
import ru.itq.fun.document.dto.SubmitCandidateStatus;
import ru.itq.fun.document.dto.registry.SubmitDocumentsRequest;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.enums.DocumentStatus;
import ru.itq.fun.document.service.DocumentService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitWorker {

    private final DocumentDao documentDao;
    private final DocumentService documentService;
    private final WorkerProperties props;

    @Scheduled(cron = "${worker.submit.cron:-}")
    public void process() {
        log.info("SubmitWorker started");
        Pageable pageable = PageRequest.of(0, props.batchSize());

        Page<Long> page;
        do {
            page = documentDao.findIdsByStatus(DocumentStatus.DRAFT, pageable);
            if (page.isEmpty()) break;

            List<Long> ids = page.getContent();
            log.info("SubmitWorker processing {} documents", ids.size());

            try {
                List<SubmitResultResponse> results =
                        documentService.batchSubmitDocuments(new SubmitDocumentsRequest(ids,
                                props.initiator(),
                                "Submit Worker"));

                long success = results.stream()
                        .filter(r -> r.status() == SubmitCandidateStatus.SUCCESS.toString())
                        .count();
                log.info("SubmitWorker batch done: {}/{} success", success, ids.size());
            } catch (Exception e) {
                log.error("SubmitWorker batch failed: {}", e.getMessage());
            }

        } while (page.hasNext());

        log.info("SubmitWorker finished");
    }
}