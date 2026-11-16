ALTER TABLE auth.verification_requests DROP CONSTRAINT uq_verification_one_per_user;
ALTER TABLE auth.verification_requests ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE auth.verification_requests ADD CONSTRAINT ck_verification_attempt_number CHECK (attempt_number > 0);
ALTER TABLE auth.verification_requests ADD CONSTRAINT uq_verification_user_attempt UNIQUE (user_id, attempt_number);
