package com.etl.runtime;

import com.etl.processor.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EmbeddedDbDuplicateResolver implements DuplicateResolver {

	private static final String CLASSIFICATION_RANKED = "RANKED";
	private static final String CLASSIFICATION_PASS_THROUGH = "PASS_THROUGH";
	private static final String CLASSIFICATION_INVALID = "INVALID";

	private final DuplicateRule rule;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final Path databaseBasePath;
	private final String jdbcUrl;
	private final Connection connection;
	private long sequence;

	public EmbeddedDbDuplicateResolver(DuplicateRule rule) {
		this.rule = Objects.requireNonNull(rule, "rule");
		try {
			this.databaseBasePath = Files.createTempFile("ordered-duplicate-", UUID.randomUUID().toString());
			Files.deleteIfExists(databaseBasePath);
			this.jdbcUrl = "jdbc:h2:file:" + databaseBasePath.toAbsolutePath() + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE";
			this.connection = DriverManager.getConnection(jdbcUrl);
			initializeSchema();
		} catch (IOException | SQLException exception) {
			throw new IllegalStateException("Failed to initialize embedded duplicate resolver.", exception);
		}
	}

	@Override
	public void accept(Object input) {
		long arrivalSequence = nextSequence();
		List<Object> keyValues = DuplicateSupport.resolveKeyValues(input, rule.keyFields());
		if (DuplicateSupport.hasIncompleteKey(keyValues)) {
			insertRecord(arrivalSequence, CLASSIFICATION_PASS_THROUGH, null, input, null, false);
			return;
		}

		List<DuplicateSupport.SortCriterionValue> sortValues = DuplicateSupport.normalizeSortValues(input, rule.orderSelectors());
		if (sortValues == null) {
			insertRecord(
					arrivalSequence,
					CLASSIFICATION_INVALID,
					null,
					input,
					rule.anchorField() + " requires a comparable value in the configured order fields "
							+ DuplicateSupport.describeOrderSelectors(rule.orderSelectors()) + ".",
					true
			);
			return;
		}

		insertRecord(arrivalSequence, CLASSIFICATION_RANKED, DuplicateSupport.buildKey(keyValues), input, null, false);
	}

	@Override
	public DuplicateResolution complete() {
		List<DuplicateDiscard> discardedRecords = new ArrayList<>();
		List<OrderedRecord> retainedRecords = new ArrayList<>();
		retainedRecords.addAll(loadPassThroughRecords());
		discardedRecords.addAll(loadInvalidRecords());
		retainedRecords.addAll(resolveRankedGroups(discardedRecords));
		retainedRecords.sort(Comparator.comparingLong(OrderedRecord::sequence));
		return new DuplicateResolution(retainedRecords.stream().map(OrderedRecord::record).toList(), discardedRecords);
	}

	@Override
	public void close() {
		try {
			connection.close();
		} catch (SQLException ignored) {
			// best effort cleanup
		}
		try {
			Files.deleteIfExists(Path.of(databaseBasePath + ".mv.db"));
			Files.deleteIfExists(Path.of(databaseBasePath + ".trace.db"));
		} catch (IOException ignored) {
			// best effort cleanup
		}
	}

	private void initializeSchema() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE staged_duplicates (
						arrival_sequence BIGINT PRIMARY KEY,
						classification VARCHAR(32) NOT NULL,
						key_value CLOB,
						payload_class VARCHAR(500) NOT NULL,
						payload_json CLOB NOT NULL,
						issue_message CLOB,
						invalid_ordering BOOLEAN NOT NULL
					)
					""");
		}
	}

	private void insertRecord(long arrivalSequence,
	                         String classification,
	                         String keyValue,
	                         Object payload,
	                         String issueMessage,
	                         boolean invalidOrderingValue) {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO staged_duplicates (arrival_sequence, classification, key_value, payload_class, payload_json, issue_message, invalid_ordering) VALUES (?, ?, ?, ?, ?, ?, ?)")
		) {
			statement.setLong(1, arrivalSequence);
			statement.setString(2, classification);
			statement.setString(3, keyValue);
			statement.setString(4, payload.getClass().getName());
			statement.setString(5, objectMapper.writeValueAsString(payload));
			statement.setString(6, issueMessage);
			statement.setBoolean(7, invalidOrderingValue);
			statement.executeUpdate();
		} catch (SQLException | IOException exception) {
			throw new IllegalStateException("Failed to stage ordered duplicate record in embedded database.", exception);
		}
	}

	private List<OrderedRecord> loadPassThroughRecords() {
		return loadOrderedRecordsByClassification(CLASSIFICATION_PASS_THROUGH);
	}

	private List<DuplicateDiscard> loadInvalidRecords() {
		List<DuplicateDiscard> discards = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT payload_class, payload_json, issue_message, invalid_ordering FROM staged_duplicates WHERE classification = ? ORDER BY arrival_sequence")) {
			statement.setString(1, CLASSIFICATION_INVALID);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					Object record = deserializePayload(resultSet.getString("payload_class"), resultSet.getString("payload_json"));
					discards.add(new DuplicateDiscard(
							record,
							new ValidationIssue(rule.anchorField(), "duplicate", resultSet.getString("issue_message")),
							resultSet.getBoolean("invalid_ordering")
					));
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to load invalid ordered duplicate records.", exception);
		}
		return discards;
	}

	private List<OrderedRecord> resolveRankedGroups(List<DuplicateDiscard> discardedRecords) {
		List<OrderedRecord> retained = new ArrayList<>();
		List<DbCandidate> currentGroup = new ArrayList<>();
		String currentKey = null;
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT arrival_sequence, key_value, payload_class, payload_json FROM staged_duplicates WHERE classification = ? ORDER BY key_value, arrival_sequence")) {
			statement.setString(1, CLASSIFICATION_RANKED);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String rowKey = resultSet.getString("key_value");
					if (currentKey != null && !currentKey.equals(rowKey)) {
						retained.addAll(resolveGroup(currentGroup, discardedRecords));
						currentGroup.clear();
					}
					currentKey = rowKey;
					Object record = deserializePayload(resultSet.getString("payload_class"), resultSet.getString("payload_json"));
					List<DuplicateSupport.SortCriterionValue> sortValues = DuplicateSupport.normalizeSortValues(record, rule.orderSelectors());
					if (sortValues == null) {
						discardedRecords.add(new DuplicateDiscard(
								record,
								new ValidationIssue(rule.anchorField(), "duplicate", rule.anchorField()
										+ " requires a comparable value in the configured order fields "
										+ DuplicateSupport.describeOrderSelectors(rule.orderSelectors()) + "."),
								true
						));
						continue;
					}
					currentGroup.add(new DbCandidate(record, sortValues, resultSet.getLong("arrival_sequence")));
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to resolve ranked duplicate groups from embedded database.", exception);
		}
		if (!currentGroup.isEmpty()) {
			retained.addAll(resolveGroup(currentGroup, discardedRecords));
		}
		return retained;
	}

	private List<OrderedRecord> resolveGroup(List<DbCandidate> group, List<DuplicateDiscard> discardedRecords) {
		group.sort(this::compareCandidates);
		List<OrderedRecord> retained = new ArrayList<>();
		DbCandidate winner = group.get(0);
		retained.add(new OrderedRecord(winner.record(), winner.arrivalSequence()));
		for (int i = 1; i < group.size(); i++) {
			DbCandidate discarded = group.get(i);
			discardedRecords.add(new DuplicateDiscard(
					discarded.record(),
					new ValidationIssue(
							rule.anchorField(),
							"duplicate",
							rule.anchorField() + " duplicate key " + rule.keyFields()
									+ " was discarded because another record already wins by order "
									+ DuplicateSupport.describeOrderSelectors(rule.orderSelectors()) + "."
					),
					false
			));
		}
		return retained;
	}

	private int compareCandidates(DbCandidate left, DbCandidate right) {
		return -DuplicateSupport.compare(
				left.sortValues(),
				right.sortValues(),
				rule.orderSelectors(),
				left.arrivalSequence(),
				right.arrivalSequence()
		);
	}

	private List<OrderedRecord> loadOrderedRecordsByClassification(String classification) {
		List<OrderedRecord> records = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT arrival_sequence, payload_class, payload_json FROM staged_duplicates WHERE classification = ? ORDER BY arrival_sequence")) {
			statement.setString(1, classification);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					records.add(new OrderedRecord(
							deserializePayload(resultSet.getString("payload_class"), resultSet.getString("payload_json")),
							resultSet.getLong("arrival_sequence")
					));
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to load staged ordered duplicate records.", exception);
		}
		return records;
	}

	private Object deserializePayload(String className, String payloadJson) {
		try {
			Class<?> payloadClass = Class.forName(className);
			return objectMapper.readValue(payloadJson, payloadClass);
		} catch (IOException | ClassNotFoundException exception) {
			throw new IllegalStateException("Failed to deserialize staged ordered duplicate payload.", exception);
		}
	}

	private long nextSequence() {
		return ++sequence;
	}

	private record OrderedRecord(Object record, long sequence) {
	}

	private record DbCandidate(Object record, List<DuplicateSupport.SortCriterionValue> sortValues, long arrivalSequence) {
	}
}


