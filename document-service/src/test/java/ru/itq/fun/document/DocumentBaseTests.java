package ru.itq.fun.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.itq.fun.document.config.H2TestConfig;
import ru.itq.fun.document.dao.ApproveRegistryDao;
import ru.itq.fun.document.dao.DocumentDao;
import ru.itq.fun.document.dto.CreateDocumentRequest;
import ru.itq.fun.document.dto.registry.SubmitDocumentsRequest;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.entity.DocumentHistory;
import ru.itq.fun.document.enums.DocumentHistoryOperation;
import ru.itq.fun.document.enums.DocumentStatus;
import ru.itq.fun.document.exception.DocumentConflictException;
import ru.itq.fun.document.exception.DocumentNotFoundException;
import ru.itq.fun.document.service.DocumentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(H2TestConfig.class)
@Transactional
class DocumentBaseTests {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentDao documentDao;
    @Autowired
    private ApproveRegistryDao approveRegistryDao;

    @Test
    void create_persistsDocumentWithDraftStatus() {
        documentService.create(new CreateDocumentRequest("Иванов", "Договор №1"));

        assertThat(documentDao.existsByAuthorAndTitle("Иванов", "Договор №1")).isTrue();
        assertThat(documentDao.findAll())
                .anyMatch(d -> d.getStatus() == DocumentStatus.DRAFT
                        && "Иванов".equals(d.getAuthor()));
    }

    @Test
    void create_duplicateAuthorAndTitle_throwsConflict() {
        saveDraft("Иванов", "Договор №1");

        assertThatThrownBy(() ->
                documentService.create(new CreateDocumentRequest("Иванов", "Договор №1"))
        ).isInstanceOf(DocumentConflictException.class);
    }

    @Test
    void getDocumentWithHistory_returnsDocumentWithHistory() {
        Document doc = saveDraft("Петров", "Договор аренды");
        documentDao.insertHistory(doc.getId(), "Петров", "SUBMIT", "первая подача");
        documentDao.insertHistory(doc.getId(), "Сидоров", "APPROVE", "согласовано");

        var response = documentService.getDocumentWithHistory(doc.getId());

        assertThat(response.id()).isEqualTo(doc.getId());
        assertThat(response.author()).isEqualTo("Петров");
        assertThat(response.history()).hasSize(2);
        assertThat(response.history()).extracting(DocumentHistory::getOperation)
                .containsExactlyInAnyOrder(DocumentHistoryOperation.SUBMIT, DocumentHistoryOperation.APPROVE);
    }

    @Test
    void getDocumentWithHistory_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> documentService.getDocumentWithHistory(Long.MAX_VALUE))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void batchSubmit_drafts_becomeSubmittedWithHistory() {
        Document d1 = saveDraft("А", "Т1");
        Document d2 = saveDraft("Б", "Т2");

        List<SubmitResultResponse> results = documentService.batchSubmitDocuments(
                new SubmitDocumentsRequest(List.of(d1.getId(), d2.getId()), "operator", "batch-test"));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SubmitResultResponse::status).containsOnly("SUCCESS");

        assertThat(documentDao.findById(d1.getId()))
                .hasValueSatisfying(d -> assertThat(d.getStatus()).isEqualTo(DocumentStatus.SUBMITTED));
        assertThat(documentDao.findById(d2.getId()))
                .hasValueSatisfying(d -> assertThat(d.getStatus()).isEqualTo(DocumentStatus.SUBMITTED));
    }

    @Test
    void batchCreate_persistsAllDocumentsWithDraftStatus() {
        var requests = List.of(
                new CreateDocumentRequest("Мистер здравствуйте 1", "Бобры 1"),
                new CreateDocumentRequest("Мистер здравствуйте 2", "Бобор 2"),
                new CreateDocumentRequest("Мистер здравствуйте 3", "Бобры 3")
        );

        documentService.batchCreate(requests);

        List<Document> saved = documentDao.findAll().stream()
                .filter(d -> List.of(
                        "Мистер здравствуйте 1",
                        "Мистер здравствуйте 2",
                        "Мистер здравствуйте 3").contains(d.getAuthor()))
                .toList();

        assertThat(saved)
                .hasSize(3)
                .allMatch(d -> d.getStatus() == DocumentStatus.DRAFT);
    }

    private Document saveDraft(String author, String title) {
        return documentDao.save(Document.builder()
                .author(author).title(title).status(DocumentStatus.DRAFT).build());
    }
}