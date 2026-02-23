## Поисковый запрос

### SQL ###

```sql
SELECT *
FROM document d
WHERE d.status = 'SUBMITTED'
  AND d.author = 'Волков В.В.'
  AND d.created_at >= '2026-02-20'
  AND d.created_at <= '2026-02-28'
ORDER BY d.created_at DESC
LIMIT 20 OFFSET 0;
```

### POSTMAN/etс ###

``` http request
http://localhost:80/api/v1/documents/search?status=APPROVED&author=generator-worker&dateFrom=2025-01-01&dateTo=2026-02-23&page=0&size=20&sort=created_at,desc
```

### EXPLAIN ###

```sql
EXPLAIN ANALYZE
SELECT *
FROM document d
WHERE d.status = 'SUBMITTED'
  AND d.author = 'Волков В.В.'
  AND d.created_at >= '2026-02-20'
  AND d.created_at <= '2026-02-28'
ORDER BY d.created_at DESC
LIMIT 20 OFFSET 0;
```

### QUERY PLAN
Limit  (cost=0.14..8.17 rows=1 width=1132) (actual time=0.054..0.055 rows=1 loops=1)

->  Index Scan using idx_document_status_author_created_at on document d  (cost=0.14..8.17 rows=1 width=1132)

(actual time=0.053..0.054 rows=1 loops=1)
Index Cond: (((status)::text = 'SUBMITTED'::text)

AND ((author)::text = 'Волков В.В.'::text) AND (created_at >= '2026-02-20 00:00:00'::timestamp without time zone)

AND (created_at <= '2026-02-28 00:00:00'::timestamp without time zone))
###

### Document Indexes ###
1. Что бы нельзя было добавить одинаковые документы одним и тем же автором. EXIST FOR CREATE

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_author_title ON document (author, title);

2. Если понадобиться искать по времени

CREATE INDEX IF NOT EXISTS idx_document_created_at ON document (created_at);
3. Индекс для метода SEARCH

CREATE INDEX IF NOT EXISTS idx_document_status_author_created_at
ON document (status, author, created_at DESC);

4. И базовый для поиска по ID(PRIMARY KEY), number unique
5. Можно разбить по партициям(время создания)  в дальнейшем и тд