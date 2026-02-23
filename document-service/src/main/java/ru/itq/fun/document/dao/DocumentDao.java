package ru.itq.fun.document.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.itq.fun.document.dto.registry.SubmitResultResponse;
import ru.itq.fun.document.entity.Document;
import ru.itq.fun.document.enums.DocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentDao extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"history"})
    @Query("select d from Document d where d.id = :id")
    Optional<Document> findDocument(@Param("id") Long id);

    @Query("select d from Document d where d.id IN :ids")
    Page<Document> findDocumentsByIds(@Param("ids") List<Long> ids, Pageable pageable);

    boolean existsByAuthorAndTitle(String author, String title);

    @Query(nativeQuery = true, value = """
            WITH input_ids AS (
                SELECT UNNEST(CAST(:ids AS bigint[])) AS id
            ),
            updated_docs AS (
                UPDATE document
                SET status = 'SUBMITTED', modified_at = now()
                WHERE id IN (:ids)
                AND status = 'DRAFT'
                RETURNING id
            ),
            history AS (
                INSERT INTO document_history (document_id, created_by, operation, created_at, comment)
                SELECT id, :initiator, 'SUBMIT', now(), :comment
                FROM updated_docs
            )
            SELECT
                i.id,
                CASE
                    WHEN ud.id IS NOT NULL THEN 'SUCCESS'
                    WHEN d.id IS NOT NULL AND d.status != 'DRAFT' THEN 'CONFLICT'
                    ELSE 'NOT_FOUND'
                END as result_status
            FROM input_ids i
            LEFT JOIN document d ON d.id = i.id
            LEFT JOIN updated_docs ud ON ud.id = i.id
            """)
    List<SubmitResultResponse> batchSubmittedDocuments(
            @Param("ids") long[] ids,
            @Param("initiator") String initiator,
            @Param("comment") String comment); //:D my batis enjoyer

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE document
            SET status = 'APPROVED', modified_at = now()
            WHERE id = :id
            AND status = 'SUBMITTED'
            """)
    int submitToApproved(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO document_history (document_id, created_by, operation, comment, created_at)
            VALUES (:documentId, :initiator, :operation, :comment, now())
            """)
    void insertHistory(
            @Param("documentId") Long documentId,
            @Param("initiator") String initiator,
            @Param("operation") String operation,
            @Param("comment") String comment);

    @Query(nativeQuery = true, value = """
            SELECT * FROM document
            WHERE id = :id
            AND status = 'SUBMITTED'
            FOR UPDATE SKIP LOCKED
            """)
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    @Query(nativeQuery = true, value = """
        SELECT * FROM document d
        WHERE
            (:status IS NULL OR d.status = :status)
            AND (:author IS NULL OR d.author = :author)
            AND (CAST(:dateFrom AS timestamp) IS NULL OR d.created_at >= CAST(:dateFrom AS timestamp))
            AND (CAST(:dateTo AS timestamp) IS NULL OR d.created_at <= CAST(:dateTo AS timestamp))
        ORDER BY d.created_at DESC
        """,
            countQuery = """
                SELECT COUNT(*) FROM document d
                WHERE
                    (:status IS NULL OR d.status = :status)
                    AND (:author IS NULL OR d.author = :author)
                    AND (CAST(:dateFrom AS timestamp) IS NULL OR d.created_at >= CAST(:dateFrom AS timestamp))
                    AND (CAST(:dateTo AS timestamp) IS NULL OR d.created_at <= CAST(:dateTo AS timestamp))
                """)
    Page<Document> searchDocuments(
            @Param("status") String status,
            @Param("author") String author,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    @Query("SELECT d.id FROM Document d WHERE d.status = :status")
    Page<Long> findIdsByStatus(@Param("status") DocumentStatus status, Pageable pageable);
}