-- Baseline retained control-plane schema for SQL Server.

CREATE TABLE dbo.controlplane_schedule (
    schedule_pk BIGINT NOT NULL PRIMARY KEY,
    schedule_id VARCHAR(80) NOT NULL UNIQUE,
    schedule_key VARCHAR(200) NOT NULL UNIQUE,
    selected_job_key VARCHAR(200) NOT NULL,
    expression VARCHAR(200) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    is_enabled BIT NOT NULL,
    is_paused BIT NOT NULL,
    description VARCHAR(2000) NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL,
    watcher_key VARCHAR(200) NULL,
    last_accepted_due_at DATETIME2 NULL
);

CREATE UNIQUE INDEX idx_schedule_pk ON dbo.controlplane_schedule (schedule_pk);
CREATE INDEX idx_schedule_selected_job ON dbo.controlplane_schedule (selected_job_key, updated_at);
CREATE INDEX idx_schedule_state ON dbo.controlplane_schedule (is_enabled, is_paused, updated_at);

CREATE TABLE dbo.controlplane_trigger_source (
    trigger_source_pk BIGINT NOT NULL PRIMARY KEY,
    source_code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(300) NULL,
    is_active BIT NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE TABLE dbo.controlplane_trigger_event (
    trigger_event_pk BIGINT NOT NULL PRIMARY KEY,
    trigger_source_pk BIGINT NULL,
    trigger_event_id VARCHAR(80) NOT NULL UNIQUE,
    job_key VARCHAR(200) NOT NULL,
    decision_status VARCHAR(50) NOT NULL,
    reason VARCHAR(200) NULL,
    requested_by VARCHAR(200) NULL,
    requested_at DATETIME2 NOT NULL,
    launched_run_pk BIGINT NULL,
    launched_run_id VARCHAR(80) NULL,
    message VARCHAR(2000) NULL,
    trigger_origin VARCHAR(50) NULL,
    schedule_pk BIGINT NULL,
    external_origin_key VARCHAR(200) NULL,
    updated_at DATETIME2 NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE UNIQUE INDEX idx_trigger_event_pk ON dbo.controlplane_trigger_event (trigger_event_pk);
CREATE INDEX idx_trigger_event_job_time ON dbo.controlplane_trigger_event (job_key, requested_at);
CREATE INDEX idx_trigger_event_origin ON dbo.controlplane_trigger_event (trigger_origin, requested_at);
CREATE INDEX idx_trigger_event_source_pk ON dbo.controlplane_trigger_event (trigger_source_pk, requested_at);
CREATE INDEX idx_trigger_event_schedule_pk_time ON dbo.controlplane_trigger_event (schedule_pk, requested_at);
CREATE INDEX idx_trigger_event_launched_run_pk ON dbo.controlplane_trigger_event (launched_run_pk, requested_at);
CREATE INDEX idx_trigger_event_launched_run_id ON dbo.controlplane_trigger_event (launched_run_id, requested_at);

CREATE TABLE dbo.controlplane_run_summary (
    run_summary_pk BIGINT NOT NULL PRIMARY KEY,
    run_record_pk BIGINT NULL,
    job_execution_id BIGINT NOT NULL UNIQUE,
    scenario VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time DATETIME2 NULL,
    end_time DATETIME2 NULL,
    duration_seconds BIGINT NULL,
    source_count BIGINT NULL,
    written_count BIGINT NULL,
    rejected_count BIGINT NULL,
    run_mode VARCHAR(80) NULL,
    recovery_policy VARCHAR(120) NULL,
    log_path VARCHAR(2000) NULL,
    last_seen_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE UNIQUE INDEX idx_run_summary_pk ON dbo.controlplane_run_summary (run_summary_pk);
CREATE INDEX idx_run_summary_run_record_pk ON dbo.controlplane_run_summary (run_record_pk);
CREATE INDEX idx_run_summary_start_time ON dbo.controlplane_run_summary (start_time, job_execution_id);

CREATE TABLE dbo.controlplane_run_record (
    run_record_pk BIGINT NOT NULL PRIMARY KEY,
    run_record_id VARCHAR(80) NOT NULL UNIQUE,
    job_execution_id BIGINT NOT NULL UNIQUE,
    trigger_event_pk BIGINT NULL,
    trigger_event_id VARCHAR(80) NULL,
    selected_job_key VARCHAR(200) NULL,
    scenario VARCHAR(200) NOT NULL,
    run_status VARCHAR(50) NOT NULL,
    started_at DATETIME2 NULL,
    finished_at DATETIME2 NULL,
    duration_seconds BIGINT NULL,
    source_count BIGINT NULL,
    written_count BIGINT NULL,
    rejected_count BIGINT NULL,
    run_mode VARCHAR(80) NULL,
    recovery_policy VARCHAR(120) NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE INDEX idx_run_record_started_at ON dbo.controlplane_run_record (started_at, job_execution_id);
CREATE UNIQUE INDEX idx_run_record_pk ON dbo.controlplane_run_record (run_record_pk);
CREATE INDEX idx_run_record_selected_job ON dbo.controlplane_run_record (selected_job_key, started_at);
CREATE INDEX idx_run_record_trigger_event_pk ON dbo.controlplane_run_record (trigger_event_pk);
CREATE INDEX idx_run_record_trigger_event ON dbo.controlplane_run_record (trigger_event_id);
CREATE INDEX idx_run_record_job_status_time ON dbo.controlplane_run_record (selected_job_key, run_status, started_at);

CREATE TABLE dbo.controlplane_step_record (
    step_record_pk BIGINT NOT NULL PRIMARY KEY,
    step_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    step_status VARCHAR(50) NOT NULL,
    started_at DATETIME2 NULL,
    finished_at DATETIME2 NULL,
    duration_seconds BIGINT NULL,
    read_count BIGINT NULL,
    write_count BIGINT NULL,
    filter_count BIGINT NULL,
    skip_count BIGINT NULL,
    rollback_count BIGINT NULL,
    rejected_count BIGINT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE INDEX idx_step_record_run_pk ON dbo.controlplane_step_record (run_record_pk, started_at);
CREATE UNIQUE INDEX idx_step_record_id_run_pk ON dbo.controlplane_step_record (step_record_id, run_record_pk);

CREATE TABLE dbo.controlplane_artifact_record (
    artifact_record_pk BIGINT NOT NULL PRIMARY KEY,
    artifact_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    step_record_id VARCHAR(80) NULL,
    artifact_role VARCHAR(80) NOT NULL,
    artifact_path VARCHAR(2000) NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE INDEX idx_artifact_record_run_pk ON dbo.controlplane_artifact_record (run_record_pk, created_at);
CREATE INDEX idx_artifact_record_step ON dbo.controlplane_artifact_record (step_record_id, created_at);

CREATE TABLE dbo.controlplane_attempt_link (
    attempt_link_pk BIGINT NOT NULL PRIMARY KEY,
    attempt_link_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    prior_run_record_pk BIGINT NULL,
    link_kind VARCHAR(50) NOT NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE INDEX idx_attempt_link_run_pk ON dbo.controlplane_attempt_link (run_record_pk, created_at);
CREATE INDEX idx_attempt_link_prior_pk ON dbo.controlplane_attempt_link (prior_run_record_pk, created_at);

CREATE TABLE dbo.controlplane_checkpoint_anchor (
    checkpoint_anchor_pk BIGINT NOT NULL PRIMARY KEY,
    checkpoint_anchor_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    step_record_pk BIGINT NULL,
    step_record_id VARCHAR(80) NULL,
    anchor_kind VARCHAR(80) NOT NULL,
    anchor_ref VARCHAR(2000) NULL,
    anchor_status VARCHAR(50) NULL,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE INDEX idx_checkpoint_anchor_run_pk ON dbo.controlplane_checkpoint_anchor (run_record_pk, created_at);
CREATE INDEX idx_checkpoint_anchor_step_pk ON dbo.controlplane_checkpoint_anchor (step_record_pk, created_at);
CREATE INDEX idx_checkpoint_anchor_step_id ON dbo.controlplane_checkpoint_anchor (step_record_id, created_at);

CREATE TABLE dbo.controlplane_log_checkpoint (
    log_path VARCHAR(2000) NOT NULL PRIMARY KEY,
    log_path_key VARCHAR(64) NULL,
    last_offset_bytes BIGINT NOT NULL,
    file_size_at_checkpoint BIGINT NOT NULL,
    file_mtime_at_checkpoint BIGINT NOT NULL,
    updated_at DATETIME2 NOT NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

CREATE UNIQUE INDEX idx_log_checkpoint_log_path_key ON dbo.controlplane_log_checkpoint (log_path_key);
CREATE INDEX idx_log_checkpoint_updated_at ON dbo.controlplane_log_checkpoint (updated_at);

CREATE TABLE dbo.controlplane_pk_sequence (
    sequence_name VARCHAR(120) NOT NULL PRIMARY KEY,
    next_value BIGINT NOT NULL,
    updated_at DATETIME2 NULL,
    created_by VARCHAR(200) NULL,
    updated_by VARCHAR(200) NULL
);

INSERT INTO dbo.controlplane_pk_sequence (sequence_name, next_value, updated_at, created_by, updated_by)
VALUES
    ('controlplane_schedule_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_trigger_event_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_run_summary_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_run_record_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_step_record_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_artifact_record_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_attempt_link_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    ('controlplane_checkpoint_anchor_pk', 1, SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap');

INSERT INTO dbo.controlplane_trigger_source (
    trigger_source_pk,
    source_code,
    display_name,
    description,
    is_active,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES
    (1, 'MANUAL', 'Manual', 'Ad hoc operator or API-triggered launch', 1, SYSDATETIME(), SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    (2, 'SCHEDULE', 'Schedule', 'Native scheduler-origin launch', 1, SYSDATETIME(), SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap'),
    (3, 'EVENT', 'Event', 'File watcher or external event-origin launch', 1, SYSDATETIME(), SYSDATETIME(), 'spring-etl-engine-bootstrap', 'spring-etl-engine-bootstrap');

