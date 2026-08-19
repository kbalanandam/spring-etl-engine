package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_run_record")
public class RunRecord {

	@Id
	@Column(name = "run_record_pk", nullable = false)
	private Long runRecordPk;

	@Column(name = "run_record_id", nullable = false, length = 80)
	private String runRecordId;

	@Column(name = "job_execution_id", nullable = false)
	private Long jobExecutionId;

	@Column(name = "trigger_event_pk")
	private Long triggerEventPk;

	@Column(name = "trigger_event_id", length = 80)
	private String triggerEventId;

	@Column(name = "selected_job_key", length = 200)
	private String selectedJobKey;

	@Column(name = "scenario", nullable = false, length = 200)
	private String scenario;

	@Column(name = "run_status", nullable = false, length = 50)
	private String runStatus;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "duration_seconds")
	private Long durationSeconds;

	@Column(name = "source_count")
	private Long sourceCount;

	@Column(name = "written_count")
	private Long writtenCount;

	@Column(name = "rejected_count")
	private Long rejectedCount;

	@Column(name = "run_mode", length = 80)
	private String runMode;

	@Column(name = "recovery_policy", length = 120)
	private String recoveryPolicy;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getRunRecordPk() {
		return runRecordPk;
	}

	public void setRunRecordPk(Long runRecordPk) {
		this.runRecordPk = runRecordPk;
	}

	public String getRunRecordId() {
		return runRecordId;
	}

	public void setRunRecordId(String runRecordId) {
		this.runRecordId = runRecordId;
	}

	public Long getJobExecutionId() {
		return jobExecutionId;
	}

	public void setJobExecutionId(Long jobExecutionId) {
		this.jobExecutionId = jobExecutionId;
	}

	public Long getTriggerEventPk() {
		return triggerEventPk;
	}

	public void setTriggerEventPk(Long triggerEventPk) {
		this.triggerEventPk = triggerEventPk;
	}

	public String getTriggerEventId() {
		return triggerEventId;
	}

	public void setTriggerEventId(String triggerEventId) {
		this.triggerEventId = triggerEventId;
	}

	public String getSelectedJobKey() {
		return selectedJobKey;
	}

	public void setSelectedJobKey(String selectedJobKey) {
		this.selectedJobKey = selectedJobKey;
	}

	public String getScenario() {
		return scenario;
	}

	public void setScenario(String scenario) {
		this.scenario = scenario;
	}

	public String getRunStatus() {
		return runStatus;
	}

	public void setRunStatus(String runStatus) {
		this.runStatus = runStatus;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}

	public Long getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(Long durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public Long getSourceCount() {
		return sourceCount;
	}

	public void setSourceCount(Long sourceCount) {
		this.sourceCount = sourceCount;
	}

	public Long getWrittenCount() {
		return writtenCount;
	}

	public void setWrittenCount(Long writtenCount) {
		this.writtenCount = writtenCount;
	}

	public Long getRejectedCount() {
		return rejectedCount;
	}

	public void setRejectedCount(Long rejectedCount) {
		this.rejectedCount = rejectedCount;
	}

	public String getRunMode() {
		return runMode;
	}

	public void setRunMode(String runMode) {
		this.runMode = runMode;
	}

	public String getRecoveryPolicy() {
		return recoveryPolicy;
	}

	public void setRecoveryPolicy(String recoveryPolicy) {
		this.recoveryPolicy = recoveryPolicy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}
}

