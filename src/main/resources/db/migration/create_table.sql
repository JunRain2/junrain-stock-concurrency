CREATE TABLE IF NOT EXISTS exception_logs
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id      VARCHAR(255) UNIQUE,
    request_content JSON,
    reason          VARCHAR(255),
    created_at      TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    is_executed     TINYINT(1) DEFAULT 0
);
