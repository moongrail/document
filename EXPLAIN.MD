# Оптимизация SQL запросов и индексы

## Поисковый запрос

### SQL запрос

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
что тут понаписал госпаде
### API запрос (Postman)

```http
GET http://localhost:80/api/v1/documents/search?status=SUBMITTED&author=Волков В.В.&dateFrom=2026-02-20&dateTo=2026-02-28&page=0&size=20&sort=created_at,desc
```

---

## EXPLAIN ANALYZE

### Команда для анализа

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

### План выполнения (ожидаемый)

```
Limit  (cost=0.14..8.17 rows=1 width=1132) (actual time=0.054..0.055 rows=1 loops=1)
  ->  Index Scan using idx_document_status_author_created_at on document d
        (cost=0.14..8.17 rows=1 width=1132) (actual time=0.053..0.054 rows=1 loops=1)
        Index Cond: (((status)::text = 'SUBMITTED'::text) 
                     AND ((author)::text = 'Волков В.В.'::text) 
                     AND (created_at >= '2026-02-20 00:00:00'::timestamp) 
                     AND (created_at <= '2026-02-28 00:00:00'::timestamp))
Planning Time: 0.5 ms
Execution Time: 0.08 ms
```

**Ключевые метрики:**
- **Index Scan** — используется индекс (хорошо)
- **Planning Time < 1 ms** — быстрое планирование
- **Execution Time < 1 ms** — быстрое выполнение

---

## Индексы таблицы Document

### 1. Уникальный индекс (защита от дубликатов)

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_document_author_title 
    ON document (author, title);
```

**Назначение:**
- Предотвращает создание одинаковых документов одним автором
- Покрывает проверку `existsByAuthorAndTitle()` в DAO

**Когда используется:**
- При создании документа (`POST /documents`)
- При пакетном создании (`POST /documents/create/batch`)

---

### 2. Индекс по времени создания

```sql
CREATE INDEX IF NOT EXISTS idx_document_created_at 
    ON document (created_at);
```

**Назначение:**
- Ускоряет поиск по диапазону дат
- Оптимизирует сортировку по времени

**Когда используется:**
- Поиск с фильтрами `dateFrom`/`dateTo`
- Сортировка `ORDER BY created_at DESC`

---

### 3. Композитный индекс для поиска (основной)

```sql
CREATE INDEX IF NOT EXISTS idx_document_status_author_created_at 
    ON document (status, author, created_at DESC);
```

**Назначение:**
- Покрывает 90% поисковых запросов
- Порядок колонок важен: `status` → `author` → `created_at`

**Когда используется:**
- `GET /documents/search` с фильтрами
- Воркеры (поиск по статусу)

**Почему такой порядок:**
1. `status` — низкая кардинальность (3 значения), но используется чаще всего
2. `author` — средняя кардинальность
3. `created_at DESC` — для сортировки без额外ного step

---

### 4. Первичный ключ и unique номер

```sql
-- PRIMARY KEY (автоматически создаёт индекс)
id BIGSERIAL PRIMARY KEY

-- Unique индекс для number
UNIQUE CONSTRAINT document_number_key (number)
```

**Назначение:**
- Быстрый поиск по ID
- Уникальность номера документа

---

## Оптимизация запросов

### Пакетные операции (Batch)

**Проблема:** N+1 запрос при обновлении статуса

**Решение:** Native query с CTE

```sql
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
```

**Преимущества:**
- 1 запрос вместо 2N+1
- Атомарность обновления
- Возвращает статус для каждого ID

---

### Конкурентная обработка (FOR UPDATE SKIP LOCKED)

**Проблема:** Воркеры блокируют друг друга

**Решение:** `SKIP LOCKED`

```sql
SELECT * FROM document
WHERE id = :id
  AND status = 'SUBMITTED'
FOR UPDATE SKIP LOCKED
```

**Преимущества:**
- Воркеры не блокируют друг друга
- Пропускает уже заблокированные строки
- Увеличивает параллелизм

---

**Преимущества:**
- Избегает лишнего приведения типов
- Лучший план выполнения
- Индекс используется корректно

---

## Рекомендации по масштабированию

### 1. Партиционирование по времени

```sql
CREATE TABLE document (
    id bigserial,
    created_at timestamp NOT NULL,
    ...
) PARTITION BY RANGE (created_at);

CREATE TABLE document_2026_q1 PARTITION OF document
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
```

**Когда применять:**
- > 10 млн записей
- Поиск преимущественно по свежим данным

---

### 2. Покрывающий индекс (Covering Index)

```sql
CREATE INDEX idx_document_covering 
    ON document (status, author, created_at DESC) 
    INCLUDE (id, number, title);
```

**Преимущества:**
- Index-only scan без обращения к таблице
- Уменьшает I/O

---

### 3. Материал представления для статистики

```sql
CREATE MATERIALIZED VIEW document_stats AS
SELECT 
    status,
    DATE(created_at) as date,
    COUNT(*) as count
FROM document
GROUP BY status, DATE(created_at);

CREATE INDEX idx_document_stats_date ON document_stats (date);
```

**Обновление:**
```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY document_stats;
```

---

## Мониторинг производительности

### Медленные запросы

```sql
-- Включить логирование медленных запросов (> 100ms)
ALTER SYSTEM SET log_min_duration_statement = 100;
SELECT pg_reload_conf();
```

### Статистика использования индексов

```sql
SELECT 
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

### Рекомендации по индексами

```sql
--Missing index suggestions
SELECT * FROM pg_stat_user_tables 
WHERE seq_scan > idx_scan 
  AND seq_tup_read > 10000;
```

---

## Тестирование производительности

### EXPLAIN (BUFFERS)

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM document
WHERE status = 'SUBMITTED'
  AND author = 'Test'
ORDER BY created_at DESC
LIMIT 20;
```

**Метрики:**
- `shared hit` — данные в кэше (хорошо)
- `shared read` — чтение с диска (плохо)
- `shared dirtied` — модифицированные страницы

### pg_stat_statements

```sql
-- Включить расширение
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Топ медленных запросов
SELECT 
    query,
    calls,
    mean_exec_time,
    total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

---

## Чек-лист оптимизации

- [ ] Все поисковые запросы используют индексы
- [ ] Нет N+1 запросов (проверять через логи Hibernate)
- [ ] Batch операции используют native queries
- [ ] Воркеры используют `SKIP LOCKED`
- [ ] `EXPLAIN ANALYZE` показывает Index Scan, а не Seq Scan
- [ ] Planning Time < 1 ms
- [ ] Execution Time < 10 ms для 95% запросов
