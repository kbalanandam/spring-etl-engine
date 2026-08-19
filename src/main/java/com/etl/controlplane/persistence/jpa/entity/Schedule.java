package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_schedule")
public class Schedule {

	@Id
	@Column(name = "schedule_pk", nullable = false)
	private Long schedulePk;

	@Column(name = "schedule_id", nullable = false, length = 80)
	private String scheduleId;

	@Column(name = "schedule_key", nullable = false, length = 200)
	private String scheduleKey;

	@Column(name = "selected_job_key", nullable = false, length = 200)
	private String selectedJobKey;

	@Column(name = "expression", nullable = false, length = 200)
	private String expression;

	@Column(name = "timezone", nullable = false, length = 100)
	private String timezone;

	@Column(name = "is_enabled", nullable = false)
	private Boolean enabled;

	@Column(name = "is_paused", nullable = false)
	private Boolean paused;

	@Column(name = "description", length = 2000)
	private String description;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	@Column(name = "watcher_key", length = 200)
	private String watcherKey;

	@Column(name = "last_accepted_due_at")
	private LocalDateTime lastAcceptedDueAt;

	public Long getSchedulePk() {
		return schedulePk;
	}

	public void setSchedulePk(Long schedulePk) {
		this.schedulePk = schedulePk;
	}

	public String getScheduleId() {
		return scheduleId;
	}

	public void setScheduleId(String scheduleId) {
		this.scheduleId = scheduleId;
	}

	public String getScheduleKey() {
		return scheduleKey;
	}

	public void setScheduleKey(String scheduleKey) {
		this.scheduleKey = scheduleKey;
	}

	public String getSelectedJobKey() {
		return selectedJobKey;
	}

	public void setSelectedJobKey(String selectedJobKey) {
		this.selectedJobKey = selectedJobKey;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public Boolean getPaused() {
		return paused;
	}

	public void setPaused(Boolean paused) {
		this.paused = paused;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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

	public String getWatcherKey() {
		return watcherKey;
	}

	public void setWatcherKey(String watcherKey) {
		this.watcherKey = watcherKey;
	}

	public LocalDateTime getLastAcceptedDueAt() {
		return lastAcceptedDueAt;
	}

	public void setLastAcceptedDueAt(LocalDateTime lastAcceptedDueAt) {
		this.lastAcceptedDueAt = lastAcceptedDueAt;
	}
}

