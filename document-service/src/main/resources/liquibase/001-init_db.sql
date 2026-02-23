CREATE SEQUENCE IF NOT EXISTS document_number_seq
    START WITH 777
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE IF NOT EXISTS document
(
    id          bigserial primary key,
    number      bigint       unique default nextval('document_number_seq'),
    author      varchar(255) not null,
    title       varchar(255)  not null,
    status      varchar(25)                  default 'DRAFT',
    created_at  timestamp                    default now(),
    modified_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_author_title ON document (author, title);
CREATE INDEX IF NOT EXISTS idx_document_created_at ON document (created_at);
CREATE INDEX idx_document_status_author_created_at
    ON document (status, author, created_at DESC);

CREATE TABLE IF NOT EXISTS document_history
(
    id          bigserial primary key,
    created_by  varchar(255)                                      not null,
    document_id bigint references document (id) on delete cascade not null,
    created_at  timestamp default now(),
    operation      varchar(25)                                       not null,
    comment     text
);

CREATE TABLE IF NOT EXISTS approve_registry
(
    id          bigserial primary key,
    approve_by  varchar(255)                                      not null,
    document_id bigint references document (id) on delete cascade not null unique,
    approve_at  timestamp default now()
);
