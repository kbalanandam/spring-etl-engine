package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_attempt_link")
public class AttemptLink {

	@Id
	@Column(name = "attempt_link_pk", nullable = false)
	private Long attemptLinkPk;

	@Column(name = "attempt_link_id", nullable = false, length = 80)
	private String attemptLinkId;

	@Column(name = "run_record_pk", nullable = false)
	private Long runRecordPk;

	@Column(name = "prior_run_record_pk")
	private Long priorRunRecordPk;

	@Column(name = "link_kind", nullable = false, length = 50)
	private String linkKind;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getAttemptLinkPk() {
		return attemptLinkPk;
	}

	public void setAttemptLinkPk(Long attemptLinkPk) {
		this.attemptLinkPk = attemptLinkPk;
	}

	public String getAttemptLinkId() {
		return attemptLinkId;
	}

	public void setAttemptLinkId(String attemptLinkId) {
		this.attemptLinkId = attemptLinkId;
	}

	public Long getRunRecordPk() {
		return runRecordPk;
	}

	public void setRunRecordPk(Long runRecordPk) {
		this.runRecordPk = runRecordPk;
	}

	public Long getPriorRunRecordPk() {
		return priorRunRecordPk;
	}

	public void setPriorRunRecordPk(Long priorRunRecordPk) {
		this.priorRunRecordPk = priorRunRecordPk;
	}

	public String getLinkKind() {
		return linkKind;
	}

	public void setLinkKind(String linkKind) {
		this.linkKind = linkKind;
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

