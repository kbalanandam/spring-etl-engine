package com.etl.source.xml.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for shipped XML source flattening strategies.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This registry owns only name-to-strategy lookup for the built-in XML strategy set. It does
 * not decide which strategy should be used for a given source; that responsibility stays with
 * {@link XmlSourceStrategySelector}. Job-specific overrides are resolved through the dedicated
 * resolver path rather than being mixed into this registry.</p>
 */
@Component
public class XmlSourceStrategyRegistry {

    private final Map<String, XmlSourceStrategy> strategiesByName;

    public XmlSourceStrategyRegistry(List<XmlSourceStrategy> strategies) {
        this.strategiesByName = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        XmlSourceStrategy::getStrategyName,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Returns the registered strategy for the supplied name or fails fast when no shipped strategy
     * is available.
     */
    public XmlSourceStrategy getRequired(String strategyName) {
        XmlSourceStrategy strategy = strategiesByName.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("No XML source strategy registered for '" + strategyName + "'.");
        }
        return strategy;
    }

    /**
     * Returns whether a shipped strategy with the supplied name is registered.
     */
    public boolean contains(String strategyName) {
        return strategiesByName.containsKey(strategyName);
    }

    /**
     * Exposes the immutable shipped strategy map for diagnostics and tests.
     */
    public Map<String, XmlSourceStrategy> getAll() {
        return strategiesByName;
    }
}

