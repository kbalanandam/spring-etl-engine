-- Bootstrap script for control-plane SQL Server schema.
-- Usage example:
--   sqlcmd -S localhost -U sa -P <password> -i scripts/sql/mssql/controlplane-bootstrap.sql
-- Token {{CONTROLPLANE_DATABASE_NAME}} is materialized by scripts/setup-controlplane-mssql.ps1.
-- Spring Batch BATCH_* metadata tables are bootstrapped separately into the same database.

IF DB_ID(N'{{CONTROLPLANE_DATABASE_NAME}}') IS NULL
BEGIN
    EXEC ('CREATE DATABASE [{{CONTROLPLANE_DATABASE_NAME}}]');
END;
GO

USE [{{CONTROLPLANE_DATABASE_NAME}}];
GO

IF OBJECT_ID(N'dbo.controlplane_schedule', N'U') IS NULL
BEGIN
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
        watcher_key VARCHAR(200) NULL,
        last_accepted_due_at DATETIME2 NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_schedule_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_schedule'))
    CREATE UNIQUE INDEX idx_schedule_pk ON dbo.controlplane_schedule (schedule_pk);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_schedule_selected_job' AND object_id = OBJECT_ID(N'dbo.controlplane_schedule'))
    CREATE INDEX idx_schedule_selected_job ON dbo.controlplane_schedule (selected_job_key, updated_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_schedule_state' AND object_id = OBJECT_ID(N'dbo.controlplane_schedule'))
    CREATE INDEX idx_schedule_state ON dbo.controlplane_schedule (is_enabled, is_paused, updated_at);
GO

IF OBJECT_ID(N'dbo.controlplane_trigger_source', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.controlplane_trigger_source (
        trigger_source_pk BIGINT NOT NULL PRIMARY KEY,
        source_code VARCHAR(50) NOT NULL UNIQUE,
        display_name VARCHAR(100) NOT NULL,
        description VARCHAR(300) NULL,
        is_active BIT NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL
    );
END;
GO

IF OBJECT_ID(N'dbo.controlplane_trigger_event', N'U') IS NULL
BEGIN
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
        external_origin_key VARCHAR(200) NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE UNIQUE INDEX idx_trigger_event_pk ON dbo.controlplane_trigger_event (trigger_event_pk);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_job_time' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_job_time ON dbo.controlplane_trigger_event (job_key, requested_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_origin' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_origin ON dbo.controlplane_trigger_event (trigger_origin, requested_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_source_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_source_pk ON dbo.controlplane_trigger_event (trigger_source_pk, requested_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_schedule_pk_time' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_schedule_pk_time ON dbo.controlplane_trigger_event (schedule_pk, requested_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_launched_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_launched_run_pk ON dbo.controlplane_trigger_event (launched_run_pk, requested_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_trigger_event_launched_run_id' AND object_id = OBJECT_ID(N'dbo.controlplane_trigger_event'))
    CREATE INDEX idx_trigger_event_launched_run_id ON dbo.controlplane_trigger_event (launched_run_id, requested_at);
GO

IF OBJECT_ID(N'dbo.controlplane_run_summary', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.controlplane_run_summary (
        job_execution_id BIGINT NOT NULL PRIMARY KEY,
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
        last_seen_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_summary_start_time' AND object_id = OBJECT_ID(N'dbo.controlplane_run_summary'))
    CREATE INDEX idx_run_summary_start_time ON dbo.controlplane_run_summary (start_time, job_execution_id);
GO

IF OBJECT_ID(N'dbo.controlplane_run_record', N'U') IS NULL
BEGIN
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
        updated_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_started_at' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE INDEX idx_run_record_started_at ON dbo.controlplane_run_record (started_at, job_execution_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE UNIQUE INDEX idx_run_record_pk ON dbo.controlplane_run_record (run_record_pk);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_selected_job' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE INDEX idx_run_record_selected_job ON dbo.controlplane_run_record (selected_job_key, started_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_trigger_event_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE INDEX idx_run_record_trigger_event_pk ON dbo.controlplane_run_record (trigger_event_pk);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_trigger_event' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE INDEX idx_run_record_trigger_event ON dbo.controlplane_run_record (trigger_event_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_run_record_job_status_time' AND object_id = OBJECT_ID(N'dbo.controlplane_run_record'))
    CREATE INDEX idx_run_record_job_status_time ON dbo.controlplane_run_record (selected_job_key, run_status, started_at);
GO

IF OBJECT_ID(N'dbo.controlplane_step_record', N'U') IS NULL
BEGIN
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
        updated_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_step_record_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_step_record'))
    CREATE INDEX idx_step_record_run_pk ON dbo.controlplane_step_record (run_record_pk, started_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_step_record_id_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_step_record'))
    CREATE UNIQUE INDEX idx_step_record_id_run_pk ON dbo.controlplane_step_record (step_record_id, run_record_pk);
GO

IF OBJECT_ID(N'dbo.controlplane_artifact_record', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.controlplane_artifact_record (
        artifact_record_pk BIGINT NOT NULL PRIMARY KEY,
        artifact_record_id VARCHAR(80) NOT NULL UNIQUE,
        run_record_pk BIGINT NOT NULL,
        step_record_id VARCHAR(80) NULL,
        artifact_role VARCHAR(80) NOT NULL,
        artifact_path VARCHAR(2000) NULL,
        created_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_artifact_record_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_artifact_record'))
    CREATE INDEX idx_artifact_record_run_pk ON dbo.controlplane_artifact_record (run_record_pk, created_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_artifact_record_step' AND object_id = OBJECT_ID(N'dbo.controlplane_artifact_record'))
    CREATE INDEX idx_artifact_record_step ON dbo.controlplane_artifact_record (step_record_id, created_at);
GO

IF OBJECT_ID(N'dbo.controlplane_attempt_link', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.controlplane_attempt_link (
        attempt_link_pk BIGINT NOT NULL PRIMARY KEY,
        attempt_link_id VARCHAR(80) NOT NULL UNIQUE,
        run_record_pk BIGINT NOT NULL,
        prior_run_record_pk BIGINT NULL,
        link_kind VARCHAR(50) NOT NULL,
        created_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_attempt_link_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_attempt_link'))
    CREATE INDEX idx_attempt_link_run_pk ON dbo.controlplane_attempt_link (run_record_pk, created_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_attempt_link_prior_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_attempt_link'))
    CREATE INDEX idx_attempt_link_prior_pk ON dbo.controlplane_attempt_link (prior_run_record_pk, created_at);
GO

IF OBJECT_ID(N'dbo.controlplane_checkpoint_anchor', N'U') IS NULL
BEGIN
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
        updated_at DATETIME2 NOT NULL
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_checkpoint_anchor_run_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_checkpoint_anchor'))
    CREATE INDEX idx_checkpoint_anchor_run_pk ON dbo.controlplane_checkpoint_anchor (run_record_pk, created_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_checkpoint_anchor_step_pk' AND object_id = OBJECT_ID(N'dbo.controlplane_checkpoint_anchor'))
    CREATE INDEX idx_checkpoint_anchor_step_pk ON dbo.controlplane_checkpoint_anchor (step_record_pk, created_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'idx_checkpoint_anchor_step_id' AND object_id = OBJECT_ID(N'dbo.controlplane_checkpoint_anchor'))
    CREATE INDEX idx_checkpoint_anchor_step_id ON dbo.controlplane_checkpoint_anchor (step_record_id, created_at);
GO

MERGE dbo.controlplane_trigger_source AS target
USING (VALUES
    (1, 'MANUAL', 'Manual', 'Ad hoc operator or API-triggered launch', 1),
    (2, 'SCHEDULE', 'Schedule', 'Native scheduler-origin launch', 1),
    (3, 'EVENT', 'Event', 'File watcher or external event-origin launch', 1)
) AS source (trigger_source_pk, source_code, display_name, description, is_active)
ON target.source_code = source.source_code
WHEN MATCHED THEN
    UPDATE SET
        target.display_name = source.display_name,
        target.description = source.description,
        target.is_active = source.is_active,
        target.updated_at = SYSDATETIME()
WHEN NOT MATCHED THEN
    INSERT (trigger_source_pk, source_code, display_name, description, is_active, created_at, updated_at)
    VALUES (source.trigger_source_pk, source.source_code, source.display_name, source.description, source.is_active, SYSDATETIME(), SYSDATETIME());
GO

