package com.etl.runtime.scenario;

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
public record ScenarioMainFlowContextDescriptor(
		boolean sharedLoggingContext,
		boolean sharedRecoveryContext,
		boolean supportsCrossSubFlowHandshake,
		boolean sharedSubFlowStatusRegistry,
		boolean supportsBlockingOnUpstreamFailure,
		List<String> visibleSubFlowNames,
		List<String> handoffAliases,
		String summary
) {

	public ScenarioMainFlowContextDescriptor {
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

	public static ScenarioMainFlowContextDescriptor defaultFor(List<ScenarioSubFlowDescriptor> subFlows,
	                                                          List<ScenarioStepDescriptor> steps,
	                                                          List<ScenarioStepLinkDescriptor> stepLinks,
	                                                          ScenarioRecoveryPolicy recoveryPolicy) {
		List<ScenarioSubFlowDescriptor> resolvedSubFlows = subFlows == null ? List.of() : List.copyOf(subFlows);
		List<ScenarioStepLinkDescriptor> resolvedLinks = stepLinks == null ? List.of() : List.copyOf(stepLinks);
		List<String> visibleSubFlowNames = resolvedSubFlows.stream()
				.map(ScenarioSubFlowDescriptor::subFlowName)
				.distinct()
				.toList();
		List<String> handoffAliases = resolvedLinks.stream()
				.map(ScenarioStepLinkDescriptor::outputAlias)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(alias -> !alias.isBlank())
				.distinct()
				.toList();
		boolean supportsCrossSubFlowHandshake = !handoffAliases.isEmpty()
				|| (steps != null && steps.size() > 1)
				|| resolvedLinks.stream().anyMatch(link -> link.linkType() != ScenarioStepLinkType.ORDER_ONLY);
		return new ScenarioMainFlowContextDescriptor(
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


