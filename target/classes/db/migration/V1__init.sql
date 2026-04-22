CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id TEXT NOT NULL, source_type TEXT NOT NULL,
    transaction_date DATE, gross_amount NUMERIC(12,2), net_amount NUMERIC(12,2),
    platform_fee NUMERIC(12,2), gst_on_fee NUMERIC(12,2),
    packaging_recovery NUMERIC(12,2), cancellation_deduction NUMERIC(12,2),
    category TEXT, description TEXT, source_file TEXT, parser_version TEXT,
    confidence_score DOUBLE PRECISION, review_flags JSONB,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (external_id, source_type));
CREATE INDEX idx_txn_source_date ON transactions(source_type, transaction_date);

CREATE TABLE IF NOT EXISTS review_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id TEXT, source_type TEXT, transaction_date DATE,
    gross_amount NUMERIC(12,2), net_amount NUMERIC(12,2),
    source_file TEXT, confidence_score DOUBLE PRECISION, review_flags JSONB,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());

CREATE TABLE IF NOT EXISTS file_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name TEXT NOT NULL, file_path TEXT NOT NULL, path_prefix TEXT NOT NULL,
    input_source TEXT NOT NULL, source_type TEXT NOT NULL, file_hash TEXT,
    file_size_bytes BIGINT, detected_format TEXT, scan_status TEXT NOT NULL,
    records_parsed INT NOT NULL DEFAULT 0,
    records_committed INT NOT NULL DEFAULT 0,
    records_queued INT NOT NULL DEFAULT 0,
    error_message TEXT, last_modified_at TIMESTAMPTZ,
    scanned_at TIMESTAMPTZ, processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE INDEX idx_audit_hash ON file_audit_log(file_hash);
CREATE INDEX idx_audit_prefix ON file_audit_log(path_prefix, scan_status);