CREATE TABLE auth.audit_events (
    id UUID PRIMARY KEY,
    service_name VARCHAR(32) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version SMALLINT NOT NULL DEFAULT 1,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_type VARCHAR(24) NOT NULL,
    actor_id VARCHAR(128),
    subject_type VARCHAR(64),
    subject_id VARCHAR(128),
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),
    previous_state VARCHAR(64),
    new_state VARCHAR(64),
    request_id VARCHAR(64),
    trace_id VARCHAR(32),
    span_id VARCHAR(16),
    client_ip VARCHAR(45),
    user_agent VARCHAR(512),
    details_json VARCHAR(4000),
    CONSTRAINT ck_auth_audit_actor_type CHECK (actor_type IN ('USER','ADMIN','SERVICE','SYSTEM','ANONYMOUS')),
    CONSTRAINT ck_auth_audit_outcome CHECK (outcome IN ('SUCCESS','FAILURE','DENIED')),
    CONSTRAINT ck_auth_audit_version CHECK (event_version > 0)
);
CREATE INDEX idx_auth_audit_occurred_at ON auth.audit_events (occurred_at DESC);
CREATE INDEX idx_auth_audit_actor ON auth.audit_events (actor_type, actor_id, occurred_at DESC);
CREATE INDEX idx_auth_audit_subject ON auth.audit_events (subject_type, subject_id, occurred_at DESC);
CREATE INDEX idx_auth_audit_target ON auth.audit_events (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_auth_audit_event_type ON auth.audit_events (event_type, occurred_at DESC);
CREATE INDEX idx_auth_audit_request_id ON auth.audit_events (request_id);
CREATE INDEX idx_auth_audit_trace_id ON auth.audit_events (trace_id);
