package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_trigger_source")
public class TriggerSource {

	@Id
	@Column(name = "trigger_source_pk", nullable = false)
	private Long triggerSourcePk;

	@Column(name = "source_code", nullable = false, length = 50)
	private String sourceCode;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "is_active", nullable = false)
	private Boolean active;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public Long getTriggerSourcePk() {
		return triggerSourcePk;
	}

	public void setTriggerSourcePk(Long triggerSourcePk) {
		this.triggerSourcePk = triggerSourcePk;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
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

