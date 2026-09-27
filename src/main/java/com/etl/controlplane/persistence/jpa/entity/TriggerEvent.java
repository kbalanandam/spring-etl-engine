package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_trigger_event")
public class TriggerEvent {

	@Id
	@Column(name = "trigger_event_pk", nullable = false)
	private Long triggerEventPk;

	@Column(name = "trigger_source_pk")
	private Long triggerSourcePk;

	@Column(name = "trigger_event_id", nullable = false, length = 80)
	private String triggerEventId;

	@Column(name = "job_key", nullable = false, length = 200)
	private String jobKey;

	@Column(name = "decision_status", nullable = false, length = 50)
	private String decisionStatus;

	@Column(name = "reason", length = 200)
	private String reason;

	@Column(name = "requested_by", length = 200)
	private String requestedBy;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "launched_run_pk")
	private Long launchedRunPk;

	@Column(name = "launched_run_id", length = 80)
	private String launchedRunId;

	@Column(name = "message", length = 2000)
	private String message;

	@Column(name = "trigger_origin", length = 50)
	private String triggerOrigin;

	@Column(name = "schedule_pk")
	private Long schedulePk;

	@Column(name = "external_origin_key", length = 200)
	private String externalOriginKey;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getTriggerEventPk() {
		return triggerEventPk;
	}

	public void setTriggerEventPk(Long triggerEventPk) {
		this.triggerEventPk = triggerEventPk;
	}

	public Long getTriggerSourcePk() {
		return triggerSourcePk;
	}

	public void setTriggerSourcePk(Long triggerSourcePk) {
		this.triggerSourcePk = triggerSourcePk;
	}

	public String getTriggerEventId() {
		return triggerEventId;
	}

	public void setTriggerEventId(String triggerEventId) {
		this.triggerEventId = triggerEventId;
	}

	public String getJobKey() {
		return jobKey;
	}

	public void setJobKey(String jobKey) {
		this.jobKey = jobKey;
	}

	public String getDecisionStatus() {
		return decisionStatus;
	}

	public void setDecisionStatus(String decisionStatus) {
		this.decisionStatus = decisionStatus;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public void setRequestedBy(String requestedBy) {
		this.requestedBy = requestedBy;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(LocalDateTime requestedAt) {
		this.requestedAt = requestedAt;
	}

	public Long getLaunchedRunPk() {
		return launchedRunPk;
	}

	public void setLaunchedRunPk(Long launchedRunPk) {
		this.launchedRunPk = launchedRunPk;
	}

	public String getLaunchedRunId() {
		return launchedRunId;
	}

	public void setLaunchedRunId(String launchedRunId) {
		this.launchedRunId = launchedRunId;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getTriggerOrigin() {
		return triggerOrigin;
	}

	public void setTriggerOrigin(String triggerOrigin) {
		this.triggerOrigin = triggerOrigin;
	}

	public Long getSchedulePk() {
		return schedulePk;
	}

	public void setSchedulePk(Long schedulePk) {
		this.schedulePk = schedulePk;
	}

	public String getExternalOriginKey() {
		return externalOriginKey;
	}

	public void setExternalOriginKey(String externalOriginKey) {
		this.externalOriginKey = externalOriginKey;
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

