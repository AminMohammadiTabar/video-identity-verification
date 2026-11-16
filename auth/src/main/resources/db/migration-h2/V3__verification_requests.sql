CREATE TABLE auth.verification_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id),
    dms_file_id UUID,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMP(6) WITH TIME ZONE,
    reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    reviewed_by UUID REFERENCES auth.users(id),
    review_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_verification_one_per_user UNIQUE (user_id)
);
CREATE INDEX idx_verification_status_submitted ON auth.verification_requests(status, submitted_at);
