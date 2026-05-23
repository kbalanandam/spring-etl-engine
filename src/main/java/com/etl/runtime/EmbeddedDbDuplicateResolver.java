package com.etl.runtime;

import com.etl.exception.RuntimeEtlException;
import com.etl.processor.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves ordered duplicate winner-selection by staging candidates in an embedded H2 database.
 *
 * <p>This resolver is used when a {@code duplicate} rule is configured with {@code orderBy}
 * and the runtime decides not to keep all ordered duplicate candidates in JVM memory. Incoming
 * records are classified as ranked candidates, pass-through records with incomplete keys, or
 * invalid records with non-comparable order values. Winner selection is performed only after the
 * full stream has been staged.</p>
 *
 * <p>The resolver stores payloads as JSON in a temporary database, resolves one winning record
 * per duplicate key according to the configured order selectors and arrival-order tie-breaks,
 * and then best-effort cleans up the temporary database files on close.</p>
 */
public final class EmbeddedDbDuplicateResolver implements DuplicateResolver {

	private static final Logger logger = LoggerFactory.getLogger(EmbeddedDbDuplicateResolver.class);

	private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
	};

	private static final String CLASSIFICATION_RANKED = "RANKED";
	private static final String CLASSIFICATION_PASS_THROUGH = "PASS_THROUGH";
	private static final String CLASSIFICATION_INVALID = "INVALID";
	private static final String CREATE_STAGED_DUPLICATES_TABLE = """
			CREATE TABLE staged_duplicates (
				arrival_sequence BIGINT PRIMARY KEY,
				classification VARCHAR(32) NOT NULL,
				key_value VARCHAR(4000),
				payload_class VARCHAR(500) NOT NULL,
				payload_json VARCHAR(1000000) NOT NULL,
				issue_message VARCHAR(2000),
				invalid_ordering BOOLEAN NOT NULL
			)
			""";
	private static final String INSERT_STAGED_DUPLICATE =
			"INSERT INTO staged_duplicates (arrival_sequence, classification, key_value, payload_class, payload_json, issue_message, invalid_ordering) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final String SELECT_INVALID_RECORDS =
			"SELECT payload_class, payload_json, issue_message, invalid_ordering FROM staged_duplicates WHERE classification = ? ORDER BY arrival_sequence";
	private static final String SELECT_RANKED_RECORDS_BY_FIRST_ARRIVAL = """
			SELECT staged.arrival_sequence, staged.key_value, staged.payload_class, staged.payload_json
			FROM staged_duplicates staged
			JOIN (
				SELECT key_value, MIN(arrival_sequence) AS first_arrival_sequence
				FROM staged_duplicates
				WHERE classification = ?
				GROUP BY key_value
			) ranked_groups ON ranked_groups.key_value = staged.key_value
			WHERE staged.classification = ?
			ORDER BY ranked_groups.first_arrival_sequence, staged.arrival_sequence
			""";
	private static final String SELECT_ORDERED_RECORDS_BY_CLASSIFICATION =
			"SELECT arrival_sequence, payload_class, payload_json FROM staged_duplicates WHERE classification = ? ORDER BY arrival_sequence";
	private static final String H2_FILE_SUFFIX = ".mv.db";
	private static final String H2_TRACE_SUFFIX = ".trace.db";
	private static final String H2_LOCK_SUFFIX = ".lock.db";

	private final DuplicateRule rule;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final Path databaseDirectory;
	private final Path databaseBasePath;
	private final Connection connection;
	private long stagedRankedCount;
	private long stagedPassThroughCount;
	private long stagedInvalidCount;
	private long sequence;

	public EmbeddedDbDuplicateResolver(DuplicateRule rule) {
		this.rule = Objects.requireNonNull(rule, "rule");
		Path tempDirectory = null;
		Path tempDatabaseBasePath = null;
		Connection tempConnection = null;
		try {
			tempDirectory = Files.createTempDirectory("ordered-duplicate-");
			tempDatabaseBasePath = tempDirectory.resolve("resolver-db");
			tempConnection = DriverManager.getConnection(toJdbcUrl(tempDatabaseBasePath));
			this.databaseDirectory = tempDirectory;
			this.databaseBasePath = tempDatabaseBasePath;
			this.connection = tempConnection;
			initializeSchema();
			logger.info("DUPLICATE_RESOLVER event=resolver_open resolverMode=embeddedDb storageEngine=h2 databaseBasePath={}", this.databaseBasePath.toAbsolutePath());
		} catch (IOException | SQLException exception) {
			closeQuietly(tempConnection);
			cleanupDatabaseFiles(tempDirectory, tempDatabaseBasePath);
			throw new RuntimeEtlException("Failed to initialize embedded duplicate resolver.", exception);
		}
	}

	@Override
	public void accept(Object input) {
		long arrivalSequence = nextSequence();
		List<Object> keyValues = DuplicateSupport.resolveKeyValues(input, rule.keyFields(), rule.identityMode());
		if (DuplicateSupport.hasIncompleteKey(keyValues)) {
			stagedPassThroughCount++;
			insertRecord(arrivalSequence, CLASSIFICATION_PASS_THROUGH, null, input, null, false);
			return;
		}

		List<DuplicateSupport.SortCriterionValue> sortValues = DuplicateSupport.normalizeSortValues(input, rule.orderSelectors());
		if (sortValues == null) {
			stagedInvalidCount++;
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

		stagedRankedCount++;
		insertRecord(arrivalSequence, CLASSIFICATION_RANKED, DuplicateSupport.buildKey(keyValues), input, null, false);
	}

	@Override
	public DuplicateResolution complete() {
		List<DuplicateDiscard> discardedRecords = new ArrayList<>();
		List<OrderedRecord> retainedRecords = new ArrayList<>();
		retainedRecords.addAll(loadPassThroughRecords());
		discardedRecords.addAll(loadInvalidRecords());
		RankedResolution rankedResolution = resolveRankedGroups(discardedRecords);
		retainedRecords.addAll(rankedResolution.retained());
		retainedRecords.sort(Comparator.comparingLong(OrderedRecord::sequence));
		List<Object> retained = retainedRecords.stream().map(OrderedRecord::record).toList();
		logger.info("DUPLICATE_RESOLVER event=resolver_summary resolverMode=embeddedDb storageEngine=h2 anchorField={} acceptedCount={} stagedRankedCount={} stagedPassThroughCount={} stagedInvalidCount={} rankedGroupCount={} retainedCount={} discardedCount={} databaseBasePath={}",
				rule.anchorField(),
				stagedRankedCount + stagedPassThroughCount + stagedInvalidCount,
				stagedRankedCount,
				stagedPassThroughCount,
				stagedInvalidCount,
				rankedResolution.groupCount(),
				retained.size(),
				discardedRecords.size(),
				databaseBasePath.toAbsolutePath());
		return new DuplicateResolution(retained, discardedRecords);
	}

	@Override
	public void close() {
		Path h2DataFile = Path.of(databaseBasePath + H2_FILE_SUFFIX);
		Path h2TraceFile = Path.of(databaseBasePath + H2_TRACE_SUFFIX);
		Path h2LockFile = Path.of(databaseBasePath + H2_LOCK_SUFFIX);
		boolean hadDataFile = Files.exists(h2DataFile);
		shutdownDatabase();
		closeQuietly(connection);
		cleanupDatabaseFiles(databaseDirectory, databaseBasePath);
		logger.info("DUPLICATE_RESOLVER event=resolver_close resolverMode=embeddedDb storageEngine=h2 databaseBasePath={} hadDataFileBeforeClose={} dataFileExistsAfterClose={} traceFileExistsAfterClose={} lockFileExistsAfterClose={} directoryExistsAfterClose={}",
				databaseBasePath.toAbsolutePath(),
				hadDataFile,
				Files.exists(h2DataFile),
				Files.exists(h2TraceFile),
				Files.exists(h2LockFile),
				Files.exists(databaseDirectory));
	}

	private static String toJdbcUrl(Path databaseBasePath) {
		return "jdbc:h2:file:" + databaseBasePath.toAbsolutePath().toString().replace('\\', '/')
				+ ";DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=FALSE";
	}

	private void shutdownDatabase() {
		try {
			if (connection.isClosed()) {
				return;
			}
			try (Statement statement = connection.createStatement()) {
				statement.execute("SHUTDOWN");
			}
		} catch (SQLException ignored) {
			// best effort cleanup
		}
	}

	private static void closeQuietly(Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.close();
		} catch (SQLException ignored) {
			// best effort cleanup
		}
	}

	private static void cleanupDatabaseFiles(Path databaseDirectory, Path databaseBasePath) {
		if (databaseBasePath != null) {
			deleteQuietly(Path.of(databaseBasePath + H2_FILE_SUFFIX));
			deleteQuietly(Path.of(databaseBasePath + H2_TRACE_SUFFIX));
			deleteQuietly(Path.of(databaseBasePath + H2_LOCK_SUFFIX));
		}
		if (databaseDirectory != null) {
			deleteQuietly(databaseDirectory);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// best effort cleanup
		}
	}

	private void initializeSchema() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(CREATE_STAGED_DUPLICATES_TABLE);
		}
	}

	private void insertRecord(long arrivalSequence,
	                         String classification,
	                         String keyValue,
	                         Object payload,
	                         String issueMessage,
	                         boolean invalidOrderingValue) {
		Object serializablePayload = normalizePayloadForStaging(payload);
		String payloadClassName = serializablePayload instanceof Map<?, ?> ? Map.class.getName() : serializablePayload.getClass().getName();
		try (PreparedStatement statement = connection.prepareStatement(
				INSERT_STAGED_DUPLICATE)
		) {
			statement.setLong(1, arrivalSequence);
			statement.setString(2, classification);
			statement.setString(3, keyValue);
			statement.setString(4, payloadClassName);
			statement.setString(5, objectMapper.writeValueAsString(serializablePayload));
			statement.setString(6, issueMessage);
			statement.setBoolean(7, invalidOrderingValue);
			statement.executeUpdate();
		} catch (SQLException | IOException exception) {
			throw new RuntimeEtlException("Failed to stage ordered duplicate record in embedded database.", exception);
		}
	}

	private Object normalizePayloadForStaging(Object payload) {
		if (payload instanceof Map<?, ?> mapPayload) {
			return new LinkedHashMap<>(mapPayload);
		}
		return payload;
	}

	private List<OrderedRecord> loadPassThroughRecords() {
		return loadOrderedRecordsByClassification(CLASSIFICATION_PASS_THROUGH);
	}

	private List<DuplicateDiscard> loadInvalidRecords() {
		List<DuplicateDiscard> discards = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(
				SELECT_INVALID_RECORDS)) {
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
			throw new RuntimeEtlException("Failed to load invalid ordered duplicate records.", exception);
		}
		return discards;
	}

	private RankedResolution resolveRankedGroups(List<DuplicateDiscard> discardedRecords) {
		List<OrderedRecord> retained = new ArrayList<>();
		List<DbCandidate> currentGroup = new ArrayList<>();
		String currentKey = null;
		int groupCount = 0;
		try (PreparedStatement statement = connection.prepareStatement(
				SELECT_RANKED_RECORDS_BY_FIRST_ARRIVAL)) {
			statement.setString(1, CLASSIFICATION_RANKED);
			statement.setString(2, CLASSIFICATION_RANKED);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String rowKey = resultSet.getString("key_value");
					if (currentKey != null && !currentKey.equals(rowKey)) {
						groupCount++;
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
			throw new RuntimeEtlException("Failed to resolve ranked duplicate groups from embedded database.", exception);
		}
		if (!currentGroup.isEmpty()) {
			groupCount++;
			retained.addAll(resolveGroup(currentGroup, discardedRecords));
		}
		return new RankedResolution(retained, groupCount);
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
				SELECT_ORDERED_RECORDS_BY_CLASSIFICATION)) {
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
			throw new RuntimeEtlException("Failed to load staged ordered duplicate records.", exception);
		}
		return records;
	}

	private Object deserializePayload(String className, String payloadJson) {
		try {
			if (Map.class.getName().equals(className)) {
				return objectMapper.readValue(payloadJson, STRING_OBJECT_MAP);
			}
			Class<?> payloadClass = Class.forName(className);
			return objectMapper.readValue(payloadJson, payloadClass);
		} catch (IOException | ClassNotFoundException exception) {
			throw new RuntimeEtlException("Failed to deserialize staged ordered duplicate payload.", exception);
		}
	}

	private long nextSequence() {
		return ++sequence;
	}

	private record OrderedRecord(Object record, long sequence) {
	}

	private record DbCandidate(Object record, List<DuplicateSupport.SortCriterionValue> sortValues, long arrivalSequence) {
	}

	private record RankedResolution(List<OrderedRecord> retained, int groupCount) {
	}
}


