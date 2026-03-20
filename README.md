# Document Service

Многопоточный Spring Boot сервис для управления документами с поддержкой workflow (Draft → Submitted → Approved) и фоновыми воркерами.

## Быстрый старт

### Предварительные требования

- **Java 21+** (`java -version`)
- **Docker** (для PostgreSQL)
- **Gradle 8.x**

### 1. Запуск базы данных

```bash
docker-compose up
```

PostgreSQL будет доступен на порту `5432`:
- Database: `documents`
- Username: `postgres`
- Password: `123`

### 2. Запуск приложения

**Через Gradle:**
```bash
# Запуск document-service (порт 80)
gradle documentServiceRun

# Запуск генератора тестовых данных
gradle generatorRun
```

**Через IntelliJ IDEA:**
- Gradle → `document-root` → `Tasks` → `application` → `documentServiceRun`

### 3. Проверка работы

```bash
curl -X POST http://localhost:80/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{"author": "Иванов И.И.", "title": "Тестовый документ"}'
```

---

## Архитектура

```
document/
├── document-service/          # Основной REST API сервис
│   ├── controller/            # REST endpoints
│   ├── service/               # Бизнес-логика
│   ├── dao/                   # Data Access Layer (JPA + native queries)
│   ├── entity/                # JPA сущности
│   ├── dto/                   # DTO records
│   ├── mapper/                # MapStruct мапперы
│   ├── worker/                # Фоновые воркеры (Submit/Approve)
│   ├── config/                # Конфигурация (Scheduler, Async)
│   └── aop/                   # AOP логирование
├── generator/                 # Утилита генерации тестовых данных
└── docker-compose.yml         # PostgreSQL контейнер
```

### Технологический стек

| Компонент | Версия |
|-----------|--------|
| Java | 21 LTS (виртуальные потоки) |
| Spring Boot | 4.0.2 |
| Spring Data JPA | ✓ |
| Hibernate | 7.x |
| PostgreSQL | 17 |
| Liquibase | ✓ |
| Lombok | ✓ |
| MapStruct | 1.5.5.Final |
| Gradle | 8.x |

---

## REST API

Base URL: `http://localhost:80/api/v1`

### Endpoints

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/documents` | Создание документа |
| GET | `/documents/{id}` | Получить документ с историей |
| GET | `/documents` | Список с пагинацией и фильтрацией по IDs |
| GET | `/documents/search` | Поиск по статусу, автору, дате |
| POST | `/documents/submit` | Пакетная отправка на submit |
| POST | `/documents/approve` | Пакетное одобрение |
| POST | `/documents/approve/concurrent-spam` | Стресс-тест параллельного approve |
| POST | `/documents/create/batch` | Пакетное создание документов |

### Примеры запросов

**Создание документа:**
```json
POST /documents
{
  "author": "Иванов И.И.",
  "title": "Договор поставки"
}
```

**Поиск документов:**
```
GET /documents/search?status=APPROVED&author=GENERATOR&dateFrom=2025-01-01&dateTo=2026-12-31&page=0&size=20&sort=created_at,desc
```

**Пакетный submit:**
```json
POST /documents/submit
{
  "ids": [1, 2, 3],
  "comment": "На согласование",
  "initiator": "USER123"
}
```

**Полный цикл workflow через Postman:**
1. Импортируйте `POSTMAN_COLLECTION.json`
2. Создайте документ → "Новый документ"
3. Установите статус DRAFT в БД: `UPDATE document SET status = 'DRAFT' WHERE id = 1;`
4. Отправьте на submit → "сабмит"
5. Одобрите → "апрув"
6. Проверьте историю → "Посмотреть по айди"

---

## База данных

### Схема

**document** — основная таблица:
- `id` — первичный ключ
- `number` — уникальный номер (sequence starting from 777)
- `author`, `title` — метаданные
- `status` — DRAFT/SUBMITTED/APPROVED
- `created_at`, `modified_at` — временные метки

**document_history** — аудит операций:
- `document_id` — FK на document
- `operation` — CREATE/SUBMIT/APPROVE
- `created_by`, `comment` — кто и зачем

**approve_registry** — реестр одобрений (уникальный constraint на document_id)

### Индексы

```sql
-- Уникальность по автору+названию
CREATE UNIQUE INDEX idx_document_author_title ON document (author, title);

-- Для поиска по дате
CREATE INDEX idx_document_created_at ON document (created_at);

-- Композитный индекс для search endpoint
CREATE INDEX idx_document_status_author_created_at 
    ON document (status, author, created_at DESC);
```

---

## Фоновые воркеры

Воркеры работают по cron и автоматически обрабатывают документы:

| Воркер | Cron | Описание |
|--------|------|----------|
| SubmitWorker | `*/50 * * * * *` | Каждые 50 сек: DRAFT → SUBMITTED |
| ApproveWorker | `*/58 * * * * *` | Каждые 58 сек: SUBMITTED → APPROVED |

**Отключение воркеров для тестирования:**
```yaml
worker:
  submit:
    cron: "-"
  approve:
    cron: "-"
```

---

## Тестирование

### Запуск тестов

```bash
gradle test
```

Все тесты используют H2 базу данных в памяти и Liquibase для инициализации схемы.

### Postman

Импортируйте `POSTMAN_COLLECTION.json` для удобного тестирования API.

---

## Логи

- **Файл логов**: `spam_logs` в корне проекта
- **Формат**: `%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n`
- **Ротация**: gzip архивы с датой (spam_logs.2026-02-23.0.gz)

---

## Конфигурация

### document-service (application.yml)

```yaml
server:
  port: 80
  context-path: /api/v1

spring:
  threads:
    virtual:
      enabled: true  # Виртуальные потоки Java 21
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 100  # Оптимизация batch операций

worker:
  batch-size: 50
  submit:
    cron: "*/50 * * * * *"
  approve:
    cron: "*/58 * * * * *"
```

### generator (application.yml)

```yaml
generator:
  count: 100        # Количество документов
  batch-size: 10    # Размер пакета
  initiator: GENERATOR

document-service:
  url: http://localhost:80/api/v1
```

---

## Производительность

### Оптимизации

1. **Виртуальные потоки Java 21** — для параллельной обработки
2. **HikariCP pool** — max 20, min idle 5
3. **JDBC batch size** — 100 записей
4. **Композитные индексы** — для search endpoint
5. **FOR UPDATE SKIP LOCKED** — для конкурентной обработки воркерами

---

## Разработка

### Добавление нового endpoint

1. Создать DTO record в `dto/`
2. Добавить метод в `DocumentService` интерфейс
3. Реализовать в `DocumentServiceImpl`
4. Добавить endpoint в `DocumentController`
5. Написать тесты в `DocumentBaseTests`

### Миграции БД

Файлы в `document-service/src/main/resources/liquibase/`:
- `001-init_db.sql` — схема
- `002-fill_data.sql` — тестовые данные
- `master.xml` — master changelog

---

## Troubleshooting

### Воркеры мешают тестированию

```sql
-- Установить статус DRAFT для ручного тестирования
UPDATE document SET status = 'DRAFT' WHERE id = 1;
```

### Ошибки подключения к БД

```bash
# Проверить статус контейнера
docker ps | grep document-postgres

# Перезапустить
docker-compose down && docker-compose up
```

### Проблемы с Java версией

```bash
java -version  # Должна быть 21+
echo $JAVA_HOME
```

---

## Ссылки

- [EXPLAIN.md](EXPLAIN.md) — оптимизация SQL запросов, планы выполнения
- [POSTMAN_COLLECTION.json](POSTMAN_COLLECTION.json) — коллекция Postman
- [QWEN.md](QWEN.md) — полная документация проекта
