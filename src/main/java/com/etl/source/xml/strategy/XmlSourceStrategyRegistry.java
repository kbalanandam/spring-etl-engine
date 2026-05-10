package com.etl.source.xml.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for available XML source flattening strategies.
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

    public XmlSourceStrategy getRequired(String strategyName) {
        XmlSourceStrategy strategy = strategiesByName.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("No XML source strategy registered for '" + strategyName + "'.");
        }
        return strategy;
    }

    public boolean contains(String strategyName) {
        return strategiesByName.containsKey(strategyName);
    }

    public Map<String, XmlSourceStrategy> getAll() {
        return strategiesByName;
    }
}

