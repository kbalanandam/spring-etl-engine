package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_run_summary")
public class RunSummary {

	@Id
	@Column(name = "run_summary_pk", nullable = false)
	private Long runSummaryPk;

	@Column(name = "run_record_pk")
	private Long runRecordPk;

	@Column(name = "job_execution_id", nullable = false)
	private Long jobExecutionId;

	@Column(name = "scenario", nullable = false, length = 200)
	private String scenario;

	@Column(name = "status", nullable = false, length = 50)
	private String status;

	@Column(name = "start_time")
	private LocalDateTime startTime;

	@Column(name = "end_time")
	private LocalDateTime endTime;

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

	@Column(name = "log_path", length = 2000)
	private String logPath;

	@Column(name = "last_seen_at", nullable = false)
	private LocalDateTime lastSeenAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getRunSummaryPk() {
		return runSummaryPk;
	}

	public void setRunSummaryPk(Long runSummaryPk) {
		this.runSummaryPk = runSummaryPk;
	}

	public Long getRunRecordPk() {
		return runRecordPk;
	}

	public void setRunRecordPk(Long runRecordPk) {
		this.runRecordPk = runRecordPk;
	}

	public Long getJobExecutionId() {
		return jobExecutionId;
	}

	public void setJobExecutionId(Long jobExecutionId) {
		this.jobExecutionId = jobExecutionId;
	}

	public String getScenario() {
		return scenario;
	}

	public void setScenario(String scenario) {
		this.scenario = scenario;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
		this.endTime = endTime;
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

	public String getLogPath() {
		return logPath;
	}

	public void setLogPath(String logPath) {
		this.logPath = logPath;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(LocalDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
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

