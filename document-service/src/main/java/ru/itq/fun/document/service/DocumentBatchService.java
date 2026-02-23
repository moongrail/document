package ru.itq.fun.document.service;

import ru.itq.fun.document.dto.registry.ApproveStatusResponse;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;

import java.util.List;

public interface DocumentBatchService {
    List<SubmitResultResponse> batchSubmitDocuments(long[] ids, String initiator, String comment);

    ApproveStatusResponse approveDocument(Long id, String initiator, String comment);
}
