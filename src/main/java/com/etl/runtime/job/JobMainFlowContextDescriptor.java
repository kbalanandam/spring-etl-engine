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

	/**
	 * Indicates whether the selected scenario exposes any named handoff aliases at the MainFlow
	 * level.
	 */
	public boolean hasHandoffs() {
		return !handoffAliases.isEmpty();
	}

	/**
	 * Returns whether the supplied alias is part of the shared MainFlow handoff contract.
	 */
	public boolean hasHandoffAlias(String alias) {
		return alias != null && handoffAliases.contains(alias);
	}

	/**
	 * Synthesizes the default MainFlow context descriptor from the selected subflows, steps, and
	 * step links.
	 *
	 * <p>The result stays intentionally control-plane oriented: it summarizes shared logging and
	 * recovery context, visible subflow names, and cross-subflow handoff aliases without changing the
	 * flat ordered Spring Batch execution plan.</p>
	 */
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


