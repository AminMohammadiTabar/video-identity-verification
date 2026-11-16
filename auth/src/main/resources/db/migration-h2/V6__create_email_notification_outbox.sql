CREATE TABLE auth.email_outbox (
    id UUID PRIMARY KEY,
    verification_request_id UUID NOT NULL REFERENCES auth.verification_requests(id) ON DELETE RESTRICT,
    subject_user_id UUID NOT NULL,
    recipient_email VARCHAR(320),
    notification_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP(6) WITH TIME ZONE,
    last_error_code VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_outbox_verification_request UNIQUE (verification_request_id),
    CONSTRAINT ck_email_outbox_type CHECK (notification_type IN ('VERIFICATION_APPROVED','VERIFICATION_REJECTED')),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','DEAD')),
    CONSTRAINT ck_email_outbox_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX idx_email_outbox_due ON auth.email_outbox (status, next_attempt_at, created_at);
CREATE INDEX idx_email_outbox_lease ON auth.email_outbox (status, locked_until);
