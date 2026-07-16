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

DELIMITER $$
CREATE PROCEDURE add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_columns VARCHAR(512),
    IN p_is_unique BOOLEAN
)
BEGIN
    DECLARE v_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND index_name = p_index_name;

    IF v_index_count = 0 THEN
        SET @ddl = CONCAT(
            'CREATE ',
            IF(p_is_unique, 'UNIQUE ', ''),
            'INDEX ', p_index_name,
            ' ON ', p_table_name,
            ' (', p_columns, ')'
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

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

CALL add_index_if_missing('controlplane_schedule', 'idx_schedule_pk', 'schedule_pk', TRUE);
CALL add_index_if_missing('controlplane_schedule', 'idx_schedule_selected_job', 'selected_job_key, updated_at', FALSE);
CALL add_index_if_missing('controlplane_schedule', 'idx_schedule_state', 'is_enabled, is_paused, updated_at', FALSE);

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

CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_pk', 'trigger_event_pk', TRUE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_job_time', 'job_key, requested_at', FALSE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_origin', 'trigger_origin, requested_at', FALSE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_source_pk', 'trigger_source_pk, requested_at', FALSE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_schedule_pk_time', 'schedule_pk, requested_at', FALSE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_launched_run_pk', 'launched_run_pk, requested_at', FALSE);
CALL add_index_if_missing('controlplane_trigger_event', 'idx_trigger_event_launched_run_id', 'launched_run_id, requested_at', FALSE);

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

CALL add_index_if_missing('controlplane_run_summary', 'idx_run_summary_start_time', 'start_time, job_execution_id', FALSE);

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

CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_started_at', 'started_at, job_execution_id', FALSE);
CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_pk', 'run_record_pk', TRUE);
CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_selected_job', 'selected_job_key, started_at', FALSE);
CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_trigger_event_pk', 'trigger_event_pk', FALSE);
CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_trigger_event', 'trigger_event_id', FALSE);
CALL add_index_if_missing('controlplane_run_record', 'idx_run_record_job_status_time', 'selected_job_key, run_status, started_at', FALSE);

CREATE TABLE IF NOT EXISTS controlplane_step_record (
    step_record_pk BIGINT PRIMARY KEY,
    step_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
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

CALL add_index_if_missing('controlplane_step_record', 'idx_step_record_run_pk', 'run_record_pk, started_at', FALSE);
CALL add_index_if_missing('controlplane_step_record', 'idx_step_record_id_run_pk', 'step_record_id, run_record_pk', TRUE);

CREATE TABLE IF NOT EXISTS controlplane_artifact_record (
    artifact_record_pk BIGINT PRIMARY KEY,
    artifact_record_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    step_record_id VARCHAR(80),
    artifact_role VARCHAR(80) NOT NULL,
    artifact_path VARCHAR(2000),
    created_at TIMESTAMP NOT NULL
);

CALL add_index_if_missing('controlplane_artifact_record', 'idx_artifact_record_run_pk', 'run_record_pk, created_at', FALSE);
CALL add_index_if_missing('controlplane_artifact_record', 'idx_artifact_record_step', 'step_record_id, created_at', FALSE);

CREATE TABLE IF NOT EXISTS controlplane_attempt_link (
    attempt_link_pk BIGINT PRIMARY KEY,
    attempt_link_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    prior_run_record_pk BIGINT,
    link_kind VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CALL add_index_if_missing('controlplane_attempt_link', 'idx_attempt_link_run_pk', 'run_record_pk, created_at', FALSE);
CALL add_index_if_missing('controlplane_attempt_link', 'idx_attempt_link_prior_pk', 'prior_run_record_pk, created_at', FALSE);

CREATE TABLE IF NOT EXISTS controlplane_checkpoint_anchor (
    checkpoint_anchor_pk BIGINT PRIMARY KEY,
    checkpoint_anchor_id VARCHAR(80) NOT NULL UNIQUE,
    run_record_pk BIGINT NOT NULL,
    step_record_pk BIGINT,
    step_record_id VARCHAR(80),
    anchor_kind VARCHAR(80) NOT NULL,
    anchor_ref VARCHAR(2000),
    anchor_status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CALL add_index_if_missing('controlplane_checkpoint_anchor', 'idx_checkpoint_anchor_run_pk', 'run_record_pk, created_at', FALSE);
CALL add_index_if_missing('controlplane_checkpoint_anchor', 'idx_checkpoint_anchor_step_pk', 'step_record_pk, created_at', FALSE);
CALL add_index_if_missing('controlplane_checkpoint_anchor', 'idx_checkpoint_anchor_step_id', 'step_record_id, created_at', FALSE);

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

DROP PROCEDURE IF EXISTS add_index_if_missing;




