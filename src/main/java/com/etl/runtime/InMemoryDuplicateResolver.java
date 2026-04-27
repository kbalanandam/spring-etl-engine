package com.etl.runtime;

import com.etl.processor.validation.ValidationIssue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryDuplicateResolver implements DuplicateResolver {

	private final DuplicateRule rule;
	private final Map<String, List<Candidate>> candidatesByKey = new LinkedHashMap<>();
	private final List<OrderedRecord> passThroughRecords = new ArrayList<>();
	private final List<DuplicateDiscard> discardedRecords = new ArrayList<>();
	private long sequence;

	public InMemoryDuplicateResolver(DuplicateRule rule) {
		this.rule = Objects.requireNonNull(rule, "rule");
	}

	@Override
	public void accept(Object input) {
		List<Object> keyValues = DuplicateSupport.resolveKeyValues(input, rule.keyFields());
		if (DuplicateSupport.hasIncompleteKey(keyValues)) {
			passThroughRecords.add(new OrderedRecord(input, nextSequence()));
			return;
		}

		List<DuplicateSupport.SortCriterionValue> sortValues = DuplicateSupport.normalizeSortValues(input, rule.orderSelectors());
		if (sortValues == null) {
			discardedRecords.add(new DuplicateDiscard(
					input,
					new ValidationIssue(
							rule.anchorField(),
							"duplicate",
							rule.anchorField() + " requires a comparable value in the configured order fields "
									+ DuplicateSupport.describeOrderSelectors(rule.orderSelectors()) + "."
					),
					true
			));
			return;
		}

		Candidate incoming = new Candidate(input, sortValues, nextSequence());
		String key = DuplicateSupport.buildKey(keyValues);
		candidatesByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(incoming);
	}

	@Override
	public DuplicateResolution complete() {
		List<OrderedRecord> retainedRecords = new ArrayList<>(passThroughRecords);
		for (List<Candidate> candidates : candidatesByKey.values()) {
			candidates.sort(this::compareCandidates);
			Candidate winner = candidates.get(0);
			retainedRecords.add(new OrderedRecord(winner.record(), winner.sequence()));
			for (int i = 1; i < candidates.size(); i++) {
				Candidate discarded = candidates.get(i);
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
		}
		retainedRecords.sort(Comparator.comparingLong(OrderedRecord::sequence));
		return new DuplicateResolution(retainedRecords.stream().map(OrderedRecord::record).toList(), discardedRecords);
	}

	private int compareCandidates(Candidate incoming, Candidate currentWinner) {
		return -DuplicateSupport.compare(
				incoming.sortValues(),
				currentWinner.sortValues(),
				rule.orderSelectors(),
				incoming.sequence(),
				currentWinner.sequence()
		);
	}

	private long nextSequence() {
		return ++sequence;
	}

	private record Candidate(Object record, List<DuplicateSupport.SortCriterionValue> sortValues, long sequence) {
	}

	private record OrderedRecord(Object record, long sequence) {
	}
}






