package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_artifact_record")
public class ArtifactRecord {

	@Id
	@Column(name = "artifact_record_pk", nullable = false)
	private Long artifactRecordPk;

	@Column(name = "artifact_record_id", nullable = false, length = 80)
	private String artifactRecordId;

	@Column(name = "run_record_pk", nullable = false)
	private Long runRecordPk;

	@Column(name = "step_record_id", length = 80)
	private String stepRecordId;

	@Column(name = "artifact_role", nullable = false, length = 80)
	private String artifactRole;

	@Column(name = "artifact_path", length = 2000)
	private String artifactPath;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getArtifactRecordPk() {
		return artifactRecordPk;
	}

	public void setArtifactRecordPk(Long artifactRecordPk) {
		this.artifactRecordPk = artifactRecordPk;
	}

	public String getArtifactRecordId() {
		return artifactRecordId;
	}

	public void setArtifactRecordId(String artifactRecordId) {
		this.artifactRecordId = artifactRecordId;
	}

	public Long getRunRecordPk() {
		return runRecordPk;
	}

	public void setRunRecordPk(Long runRecordPk) {
		this.runRecordPk = runRecordPk;
	}

	public String getStepRecordId() {
		return stepRecordId;
	}

	public void setStepRecordId(String stepRecordId) {
		this.stepRecordId = stepRecordId;
	}

	public String getArtifactRole() {
		return artifactRole;
	}

	public void setArtifactRole(String artifactRole) {
		this.artifactRole = artifactRole;
	}

	public String getArtifactPath() {
		return artifactPath;
	}

	public void setArtifactPath(String artifactPath) {
		this.artifactPath = artifactPath;
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


