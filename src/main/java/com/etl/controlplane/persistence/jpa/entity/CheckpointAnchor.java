package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_checkpoint_anchor")
public class CheckpointAnchor {

	@Id
	@Column(name = "checkpoint_anchor_pk", nullable = false)
	private Long checkpointAnchorPk;

	@Column(name = "checkpoint_anchor_id", nullable = false, length = 80)
	private String checkpointAnchorId;

	@Column(name = "run_record_pk", nullable = false)
	private Long runRecordPk;

	@Column(name = "step_record_pk")
	private Long stepRecordPk;

	@Column(name = "step_record_id", length = 80)
	private String stepRecordId;

	@Column(name = "anchor_kind", nullable = false, length = 80)
	private String anchorKind;

	@Column(name = "anchor_ref", length = 2000)
	private String anchorRef;

	@Column(name = "anchor_status", length = 50)
	private String anchorStatus;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getCheckpointAnchorPk() {
		return checkpointAnchorPk;
	}

	public void setCheckpointAnchorPk(Long checkpointAnchorPk) {
		this.checkpointAnchorPk = checkpointAnchorPk;
	}

	public String getCheckpointAnchorId() {
		return checkpointAnchorId;
	}

	public void setCheckpointAnchorId(String checkpointAnchorId) {
		this.checkpointAnchorId = checkpointAnchorId;
	}

	public Long getRunRecordPk() {
		return runRecordPk;
	}

	public void setRunRecordPk(Long runRecordPk) {
		this.runRecordPk = runRecordPk;
	}

	public Long getStepRecordPk() {
		return stepRecordPk;
	}

	public void setStepRecordPk(Long stepRecordPk) {
		this.stepRecordPk = stepRecordPk;
	}

	public String getStepRecordId() {
		return stepRecordId;
	}

	public void setStepRecordId(String stepRecordId) {
		this.stepRecordId = stepRecordId;
	}

	public String getAnchorKind() {
		return anchorKind;
	}

	public void setAnchorKind(String anchorKind) {
		this.anchorKind = anchorKind;
	}

	public String getAnchorRef() {
		return anchorRef;
	}

	public void setAnchorRef(String anchorRef) {
		this.anchorRef = anchorRef;
	}

	public String getAnchorStatus() {
		return anchorStatus;
	}

	public void setAnchorStatus(String anchorStatus) {
		this.anchorStatus = anchorStatus;
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

