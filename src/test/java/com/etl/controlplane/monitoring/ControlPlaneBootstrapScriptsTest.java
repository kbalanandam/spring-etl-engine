package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneBootstrapScriptsTest {

	@Test
	void mysqlBootstrapSkipsLegacyRepairAndSeedsPkSequencesWithStaticFloor() throws IOException {
		String sql = normalizedSql(Path.of("scripts", "sql", "mysql", "controlplane-bootstrap.sql"));

		assertAll(
				() -> assertFalse(sql.contains("add_column_if_missing")),
				() -> assertFalse(sql.contains("update controlplane_run_summary set run_summary_pk = job_execution_id where run_summary_pk is null;")),
				() -> assertFalse(sql.contains("update controlplane_run_summary rs join controlplane_run_record rr on rr.job_execution_id = rs.job_execution_id set rs.run_record_pk = rr.run_record_pk where rs.run_record_pk is null;")),
				() -> assertTrue(sql.contains("call upsert_pk_sequence_floor('controlplane_run_summary_pk', 1);")),
				() -> assertTrue(sql.contains("call upsert_pk_sequence_floor('controlplane_run_record_pk', 1);"))
		);
	}

	@Test
	void sqlServerBootstrapSkipsLegacyRepairAndSeedsPkSequencesWithStaticFloor() throws IOException {
		String sql = normalizedSql(Path.of("scripts", "sql", "mssql", "controlplane-bootstrap.sql"));

		assertAll(
				() -> assertFalse(sql.contains("if col_length(n'dbo.controlplane_run_summary', n'run_summary_pk') is null alter table dbo.controlplane_run_summary add run_summary_pk bigint null;")),
				() -> assertFalse(sql.contains("if col_length(n'dbo.controlplane_run_summary', n'run_record_pk') is null alter table dbo.controlplane_run_summary add run_record_pk bigint null;")),
				() -> assertFalse(sql.contains("update dbo.controlplane_run_summary set run_summary_pk = job_execution_id where run_summary_pk is null;")),
				() -> assertFalse(sql.contains("update rs set rs.run_record_pk = rr.run_record_pk from dbo.controlplane_run_summary rs join dbo.controlplane_run_record rr on rr.job_execution_id = rs.job_execution_id where rs.run_record_pk is null;")),
				() -> assertTrue(sql.contains("merge dbo.controlplane_pk_sequence as target")),
				() -> assertTrue(sql.contains("(n'controlplane_run_summary_pk', 1)")),
				() -> assertTrue(sql.contains("(n'controlplane_run_record_pk', 1)"))
		);
	}

	private String normalizedSql(Path path) throws IOException {
		return Files.readString(path)
				.replace("\r", " ")
				.replace("\n", " ")
				.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase(Locale.ROOT);
	}
}


