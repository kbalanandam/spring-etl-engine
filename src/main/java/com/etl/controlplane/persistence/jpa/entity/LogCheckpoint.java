package com.etl.controlplane.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "controlplane_log_checkpoint")
public class LogCheckpoint {

	@Id
	@Column(name = "log_path", nullable = false, length = 2000)
	private String logPath;

	@Column(name = "log_path_key", length = 64)
	private String logPathKey;

	@Column(name = "last_offset_bytes", nullable = false)
	private Long lastOffsetBytes;

	@Column(name = "file_size_at_checkpoint", nullable = false)
	private Long fileSizeAtCheckpoint;

	@Column(name = "file_mtime_at_checkpoint", nullable = false)
	private Long fileMtimeAtCheckpoint;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "created_by", length = 200)
	private String createdBy;

	@Column(name = "updated_by", length = 200)
	private String updatedBy;

	public String getLogPath() {
		return logPath;
	}

	public void setLogPath(String logPath) {
		this.logPath = logPath;
	}

	public String getLogPathKey() {
		return logPathKey;
	}

	public void setLogPathKey(String logPathKey) {
		this.logPathKey = logPathKey;
	}

	public Long getLastOffsetBytes() {
		return lastOffsetBytes;
	}

	public void setLastOffsetBytes(Long lastOffsetBytes) {
		this.lastOffsetBytes = lastOffsetBytes;
	}

	public Long getFileSizeAtCheckpoint() {
		return fileSizeAtCheckpoint;
	}

	public void setFileSizeAtCheckpoint(Long fileSizeAtCheckpoint) {
		this.fileSizeAtCheckpoint = fileSizeAtCheckpoint;
	}

	public Long getFileMtimeAtCheckpoint() {
		return fileMtimeAtCheckpoint;
	}

	public void setFileMtimeAtCheckpoint(Long fileMtimeAtCheckpoint) {
		this.fileMtimeAtCheckpoint = fileMtimeAtCheckpoint;
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

