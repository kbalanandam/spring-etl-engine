package com.etl.extension;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionConflictPolicyTest {

    @Test
    void resolve_prefersOverrideCandidate() {
        ExtensionConflictPolicy.ProviderMetadata builtIn = new ExtensionConflictPolicy.ProviderMetadata("builtin", false);
        ExtensionConflictPolicy.ProviderMetadata externalOverride = new ExtensionConflictPolicy.ProviderMetadata("external", true);

        Map<String, String> resolved = ExtensionConflictPolicy.resolve(
                List.of(
                        new ExtensionConflictPolicy.Candidate<>("csv", "builtin-reader", builtIn),
                        new ExtensionConflictPolicy.Candidate<>("csv", "external-reader", externalOverride)
                ),
                noOpReporter()
        );

        assertEquals("external-reader", resolved.get("csv"));
    }

    @Test
    void resolve_failsWhenDuplicateWithoutExplicitOverrideWinner() {
        ExtensionConflictPolicy.ProviderMetadata first = new ExtensionConflictPolicy.ProviderMetadata("p1", false);
        ExtensionConflictPolicy.ProviderMetadata second = new ExtensionConflictPolicy.ProviderMetadata("p2", false);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                ExtensionConflictPolicy.resolve(
                        List.of(
                                new ExtensionConflictPolicy.Candidate<>("csv", "r1", first),
                                new ExtensionConflictPolicy.Candidate<>("csv", "r2", second)
                        ),
                        noOpReporter()
                )
        );

        assertEquals("duplicate:csv:p1:p2", failure.getMessage());
    }

    private static ExtensionConflictPolicy.ConflictReporter<String> noOpReporter() {
        return new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(String key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
            }

            @Override
            public void onIgnored(String key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
            }

            @Override
            public RuntimeException duplicateFailure(String key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new IllegalStateException("duplicate:" + key + ":" + existing.providerId() + ":" + candidate.providerId());
            }
        };
    }
}
