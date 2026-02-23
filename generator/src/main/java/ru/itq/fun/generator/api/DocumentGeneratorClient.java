package ru.itq.fun.generator.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.itq.fun.generator.api.dto.CreateDocumentRequest;
import ru.itq.fun.generator.config.GeneratorProperties;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentGeneratorClient {

    public static final int BOUND = 2_000_000_000;
    private final RestClient restClient;
    private final GeneratorProperties props;
    private final Random random = new Random();

    //can be scheduled job
    public void generateDocuments() {
        int total = props.count();
        int batchSize = props.batchSize();

        log.info("Starting generation of {} documents in batches of {}", total, batchSize);

        for (int i = 0; i < total; i += batchSize) {

            int end = Math.min(i + batchSize, total);
            List<CreateDocumentRequest> batch = IntStream.range(i, end)
                    .mapToObj(n -> buildRequest(random.nextInt(BOUND)))
                    .toList();
            try {
                sendBatch(batch);
                log.info("Created documents [{} - {}]", i, end);
            } catch (Exception e) {
                log.error("Failed to create batch [{} - {}]: {}", i, end, e.getMessage());
            }
        }

        log.info("Generation complete");
    }

    private void sendBatch(List<CreateDocumentRequest> batch) {
        restClient.post()
                .uri(props.documentServiceUrl() + "/documents/create/batch")
                .body(batch)
                .retrieve()
                .toBodilessEntity();
    }

    private CreateDocumentRequest buildRequest(int i) {

        return new CreateDocumentRequest(
                props.initiator(),
                i + ": Generated Document : " + UUID.randomUUID()

        );
    }
}
