-- The DMS owns its own schema and its own Flyway history table.
-- No foreign key to auth.users: DMS is independently deployable.
CREATE TABLE dms.files (
    id UUID PRIMARY KEY,
    subsystem_key VARCHAR(64) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_reference VARCHAR(128) NOT NULL,
    uploaded_by_user_id UUID NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(128) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL UNIQUE,
    content_type VARCHAR(128) NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    purged_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_dms_files_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_dms_files_subsystem CHECK (length(btrim(subsystem_key)) > 0),
    CONSTRAINT ck_dms_files_owner CHECK (length(btrim(owner_reference)) > 0),
    CONSTRAINT ck_dms_files_owner_type CHECK (owner_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_dms_files_status CHECK (status IN ('ACTIVE', 'DELETED', 'PURGE_PENDING', 'PURGED'))
);

-- Supports searching a specific owner's files in a subsystem, most recent first.
CREATE INDEX idx_dms_files_owner_created
    ON dms.files (subsystem_key, owner_type, owner_reference, created_at DESC);
CREATE INDEX idx_dms_files_status ON dms.files (status);
