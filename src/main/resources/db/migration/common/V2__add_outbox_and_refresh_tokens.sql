-- Create outbox events table for Transactional Outbox Pattern
CREATE TABLE outbox_events (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(200) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at   TIMESTAMP,
    retry_count    INT          NOT NULL DEFAULT 0,
    failure_reason TEXT,
    correlation_id VARCHAR(64)
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

-- Create refresh tokens table
CREATE TABLE refresh_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_expires_at ON refresh_tokens(expires_at);
