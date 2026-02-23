CREATE SEQUENCE IF NOT EXISTS document_number_seq
    START WITH 777
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE IF NOT EXISTS document
(
    id          BIGSERIAL PRIMARY KEY,
    number      BIGINT DEFAULT (NEXTVAL('document_number_seq')) UNIQUE,
    author      VARCHAR(255) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    status      VARCHAR(25)  DEFAULT 'DRAFT',
    created_at  TIMESTAMP    DEFAULT NOW(),
    modified_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_author_title ON document (author, title);
CREATE INDEX IF NOT EXISTS idx_document_created_at ON document (created_at);
CREATE INDEX IF NOT EXISTS idx_document_status_author_created_at
    ON document (status, author, created_at DESC);

CREATE TABLE IF NOT EXISTS document_history
(
    id          BIGSERIAL PRIMARY KEY,
    created_by  VARCHAR(255) NOT NULL,
    document_id BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    created_at  TIMESTAMP DEFAULT NOW(),
    operation   VARCHAR(25)  NOT NULL,
    comment     TEXT
);

CREATE TABLE IF NOT EXISTS approve_registry
(
    id          BIGSERIAL PRIMARY KEY,
    approve_by  VARCHAR(255) NOT NULL,
    document_id BIGINT       NOT NULL UNIQUE REFERENCES document (id) ON DELETE CASCADE,
    approve_at  TIMESTAMP DEFAULT NOW()
);