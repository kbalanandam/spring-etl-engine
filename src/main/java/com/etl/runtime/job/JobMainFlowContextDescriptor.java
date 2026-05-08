package com.etl.runtime.job;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Descriptor for the shared MainFlow context that spans all contained subflows and steps.
 *
 * <p>This is intentionally control-plane metadata, not a business-payload container. It records
 * the shared logging/recovery context and the named handoff aliases that the MainFlow must keep
 * visible across reusable subflow execution.</p>
 */
public record JobMainFlowContextDescriptor(
		boolean sharedLoggingContext,
		boolean sharedRecoveryContext,
		boolean supportsCrossSubFlowHandshake,
		boolean sharedSubFlowStatusRegistry,
		boolean supportsBlockingOnUpstreamFailure,
		List<String> visibleSubFlowNames,
		List<String> handoffAliases,
		String summary
) {

	public JobMainFlowContextDescriptor {
		visibleSubFlowNames = visibleSubFlowNames == null ? List.of() : List.copyOf(new LinkedHashSet<>(visibleSubFlowNames));
		handoffAliases = handoffAliases == null ? List.of() : List.copyOf(new LinkedHashSet<>(handoffAliases));
		summary = summary == null || summary.isBlank()
				? buildSummary(sharedLoggingContext, sharedRecoveryContext, supportsCrossSubFlowHandshake,
				sharedSubFlowStatusRegistry, supportsBlockingOnUpstreamFailure, visibleSubFlowNames, handoffAliases)
				: summary.trim();
	}

	public boolean hasHandoffs() {
		return !handoffAliases.isEmpty();
	}

	public boolean hasHandoffAlias(String alias) {
		return alias != null && handoffAliases.contains(alias);
	}

	public static JobMainFlowContextDescriptor defaultFor(List<JobSubFlowDescriptor> subFlows,
	                                                          List<JobStepDescriptor> steps,
	                                                          List<JobStepLinkDescriptor> stepLinks,
	                                                          JobRecoveryPolicy recoveryPolicy) {
		List<JobSubFlowDescriptor> resolvedSubFlows = subFlows == null ? List.of() : List.copyOf(subFlows);
		List<JobStepLinkDescriptor> resolvedLinks = stepLinks == null ? List.of() : List.copyOf(stepLinks);
		List<String> visibleSubFlowNames = resolvedSubFlows.stream()
				.map(JobSubFlowDescriptor::subFlowName)
				.distinct()
				.toList();
		List<String> handoffAliases = resolvedLinks.stream()
				.map(JobStepLinkDescriptor::outputAlias)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(alias -> !alias.isBlank())
				.distinct()
				.toList();
		boolean supportsCrossSubFlowHandshake = !handoffAliases.isEmpty()
				|| (steps != null && steps.size() > 1)
				|| resolvedLinks.stream().anyMatch(link -> link.linkType() != JobStepLinkType.ORDER_ONLY);
		return new JobMainFlowContextDescriptor(
				true,
				recoveryPolicy != null,
				supportsCrossSubFlowHandshake,
				!visibleSubFlowNames.isEmpty(),
				resolvedSubFlows.size() > 1,
				visibleSubFlowNames,
				handoffAliases,
				null
		);
	}

	private static String buildSummary(boolean sharedLoggingContext,
	                                  boolean sharedRecoveryContext,
	                                  boolean supportsCrossSubFlowHandshake,
	                                  boolean sharedSubFlowStatusRegistry,
	                                  boolean supportsBlockingOnUpstreamFailure,
	                                  List<String> visibleSubFlowNames,
	                                  List<String> handoffAliases) {
		String subFlowSummary = visibleSubFlowNames.isEmpty()
				? "visibleSubFlows=none"
				: "visibleSubFlows=" + String.join(",", visibleSubFlowNames);
		String handoffSummary = handoffAliases.isEmpty()
				? "handoffAliases=none"
				: "handoffAliases=" + String.join(",", handoffAliases);
		return "sharedLoggingContext=" + sharedLoggingContext
				+ ", sharedRecoveryContext=" + sharedRecoveryContext
				+ ", supportsCrossSubFlowHandshake=" + supportsCrossSubFlowHandshake
				+ ", sharedSubFlowStatusRegistry=" + sharedSubFlowStatusRegistry
				+ ", supportsBlockingOnUpstreamFailure=" + supportsBlockingOnUpstreamFailure
				+ ", " + subFlowSummary
				+ ", " + handoffSummary;
	}
}


