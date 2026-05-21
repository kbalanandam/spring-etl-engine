package com.etl.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared extension conflict policy for keyed registrations with explicit override intent.
 */
public final class ExtensionConflictPolicy {

    private ExtensionConflictPolicy() {
    }

    public static <K, V> Map<K, V> resolve(List<Candidate<K, V>> candidates,
                                           ConflictReporter<K> conflictReporter) {
        Map<K, Candidate<K, V>> selected = new LinkedHashMap<>();
        for (Candidate<K, V> candidate : candidates) {
            Candidate<K, V> existing = selected.get(candidate.key());
            if (existing == null) {
                selected.put(candidate.key(), candidate);
                continue;
            }

            if (candidate.provider().isOverride() && !existing.provider().isOverride()) {
                conflictReporter.onOverride(candidate.key(), candidate.provider(), existing.provider());
                selected.put(candidate.key(), candidate);
                continue;
            }

            if (!candidate.provider().isOverride() && existing.provider().isOverride()) {
                conflictReporter.onIgnored(candidate.key(), candidate.provider(), existing.provider());
                continue;
            }

            throw conflictReporter.duplicateFailure(candidate.key(), existing.provider(), candidate.provider());
        }

        Map<K, V> resolved = new LinkedHashMap<>();
        for (Map.Entry<K, Candidate<K, V>> entry : selected.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().registration());
        }
        return Map.copyOf(resolved);
    }

    public record ProviderMetadata(String providerId, boolean isOverride) {
    }

    public record Candidate<K, V>(K key, V registration, ProviderMetadata provider) {
    }

    public interface ConflictReporter<K> {
        void onOverride(K key, ProviderMetadata winner, ProviderMetadata replaced);

        void onIgnored(K key, ProviderMetadata ignored, ProviderMetadata winner);

        RuntimeException duplicateFailure(K key, ProviderMetadata existing, ProviderMetadata candidate);
    }
}
