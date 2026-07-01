-- Bootstrap script for control-plane MySQL schema.
-- Usage example:
--   mysql -h localhost -P 3306 -u root -p < scripts/sql/mysql/controlplane-bootstrap.sql
-- Token {{CONTROLPLANE_DATABASE_NAME}} is materialized by scripts/setup-controlplane-mysql.ps1.
-- Spring Batch BATCH_* metadata tables are bootstrapped separately into the same database
-- so control-plane trigger/run projections can read worker batch metadata without cross-db joins.

CREATE DATABASE IF NOT EXISTS `{{CONTROLPLANE_DATABASE_NAME}}`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `{{CONTROLPLANE_DATABASE_NAME}}`;

CREATE TABLE IF NOT EXISTS controlplane_schedule (
    schedule_pk BIGINT PRIMARY KEY,
    schedule_id VARCHAR(80) NOT NULL UNIQUE,
    schedule_key VARCHAR(200) NOT NULL UNIQUE,
    selected_job_key VARCHAR(200) NOT NULL,
    expression VARCHAR(200) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN NOT NULL,
    is_paused BOOLEAN NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    watcher_key VARCHAR(200),
    last_accepted_due_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX idx_schedule_pk ON controlplane_schedule (schedule_pk);
CREATE INDEX idx_schedule_selected_job ON controlplane_schedule (selected_job_key, updated_at);
CREATE INDEX idx_schedule_state ON controlplane_schedule (is_enabled, is_paused, updated_at);

CREATE TABLE IF NOT EXISTS controlplane_trigger_source (
    trigger_source_pk BIGINT PRIMARY KEY,
    source_code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS controlplane_trigger_event (
    trigger_event_pk BIGINT PRIMARY KEY,
    trigger_source_pk BIGINT,
    trigger_event_id VARCHAR(80) NOT NULL UNIQUE,
    job_key VARCHAR(200) NOT NULL,
    decision_status VARCHAR(50) NOT NULL,
    reason VARCHAR(200),
    requested_by VARCHAR(200),
    requested_at TIMESTAMP NOT NULL,
    launched_run_pk BIGINT,
    launched_run_id VARCHAR(80),
    message VARCHAR(2000),
    trigger_origin VARCHAR(50),
    schedule_pk BIGINT,
    external_origin_key VARCHAR(200)
);

CREATE UNIQUE INDEX idx_trigger_event_pk ON controlplane_trigger_event (trigger_event_pk);
CREATE INDEX idx_trigger_event_job_time ON controlplane_trigger_event (job_key, requested_at);
CREATE INDEX idx_trigger_event_origin ON controlplane_trigger_event (trigger_origin, requested_at);
CREATE INDEX idx_trigger_event_source_pk ON controlplane_trigger_event (trigger_source_pk, requested_at);
CREATE INDEX idx_trigger_event_schedule_pk_time ON controlplane_trigger_event (schedule_pk, requested_at);
CREATE INDEX idx_trigger_event_launched_run_pk ON controlplane_trigger_event (launched_run_pk, requested_at);
CREATE INDEX idx_trigger_event_launched_run_id ON controlplane_trigger_event (launched_run_id, requested_at);

CREATE TABLE IF NOT EXISTS controlplane_run_summary (
    job_execution_id BIGINT PRIMARY KEY,
    scenario VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    duration_seconds BIGINT,
    source_count BIGINT,
    written_count BIGINT,
    rejected_count BIGINT,
    run_mode VARCHAR(80),
    recovery_policy VARCHAR(120),
    log_path VARCHAR(2000),
    last_seen_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_run_summary_start_time ON controlplane_run_summary (start_time, job_execution_id);

CREATE TABLE IF NOT EXISTS controlplane_run_record (
    run_record_pk BIGINT PRIMARY KEY,
    run_record_id VARCHAR(80) NOT NULL UNIQUE,
    job_execution_id BIGINT NOT NULL UNIQUE,
    trigger_event_pk BIGINT,
    trigger_event_id VARCHAR(80),
    selected_job_key VARCHAR(200),
    scenario VARCHAR(200) NOT NULL,
    run_status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    duration_seconds BIGINT,
    source_count BIGINT,
    written_count BIGINT,
    rejected_count BIGINT,
    run_mode VARCHAR(80),
    recovery_policy VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_run_record_started_at ON controlplane_run_record (started_at, job_execution_id);
CREATE UNIQUE INDEX idx_run_record_pk ON controlplane_run_record (run_record_pk);
CREATE INDEX idx_run_record_selected_job ON controlplane_run_record (selected_job_key, started_at);
CREATE INDEX idx_run_record_trigger_event_pk ON controlplane_run_record (trigger_event_pk);
CREATE INDEX idx_run_record_trigger_event ON controlplane_run_record (trigger_event_id);
CREATE INDEX idx_run_record_job_status_time ON controlplane_run_record (selected_job_key, run_status, started_at);

CREATE TABLE IF NOT EXISTS controlplane_step_record (
    step_record_pk BIGINT PRIMARY KEY,
    step_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_id VARCHAR(80) NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    step_status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    duration_seconds BIGINT,
    read_count BIGINT,
    write_count BIGINT,
    filter_count BIGINT,
    skip_count BIGINT,
    rollback_count BIGINT,
    rejected_count BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_step_record_run ON controlplane_step_record (run_record_id, started_at);
CREATE UNIQUE INDEX idx_step_record_id_run ON controlplane_step_record (step_record_id, run_record_id);

CREATE TABLE IF NOT EXISTS controlplane_artifact_record (
    artifact_record_pk BIGINT PRIMARY KEY,
    artifact_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_id VARCHAR(80) NOT NULL,
    step_record_id VARCHAR(80),
    artifact_role VARCHAR(80) NOT NULL,
    artifact_path VARCHAR(2000),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_artifact_record_run ON controlplane_artifact_record (run_record_id, created_at);
CREATE INDEX idx_artifact_record_step ON controlplane_artifact_record (step_record_id, created_at);

CREATE TABLE IF NOT EXISTS controlplane_attempt_link (
    attempt_link_pk BIGINT PRIMARY KEY,
    attempt_link_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_id VARCHAR(80) NOT NULL,
    prior_run_record_id VARCHAR(80),
    link_kind VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_attempt_link_run ON controlplane_attempt_link (run_record_id, created_at);
CREATE INDEX idx_attempt_link_prior ON controlplane_attempt_link (prior_run_record_id, created_at);

CREATE TABLE IF NOT EXISTS controlplane_checkpoint_anchor (
    checkpoint_anchor_pk BIGINT PRIMARY KEY,
    checkpoint_anchor_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_id VARCHAR(80) NOT NULL,
    step_record_id VARCHAR(80),
    anchor_kind VARCHAR(80) NOT NULL,
    anchor_ref VARCHAR(2000),
    anchor_status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_checkpoint_anchor_run ON controlplane_checkpoint_anchor (run_record_id, created_at);
CREATE INDEX idx_checkpoint_anchor_step ON controlplane_checkpoint_anchor (step_record_id, created_at);

INSERT INTO controlplane_trigger_source (
    trigger_source_pk,
    source_code,
    display_name,
    description,
    is_active,
    created_at,
    updated_at
) VALUES
    (1, 'MANUAL', 'Manual', 'Ad hoc operator or API-triggered launch', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'SCHEDULE', 'Schedule', 'Native scheduler-origin launch', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'EVENT', 'Event', 'File watcher or external event-origin launch', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    is_active = VALUES(is_active),
    updated_at = CURRENT_TIMESTAMP;




