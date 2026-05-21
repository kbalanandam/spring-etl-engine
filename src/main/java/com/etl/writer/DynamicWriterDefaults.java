package com.etl.writer;

import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import com.etl.exception.FactoryException;
import com.etl.writer.spi.BuiltInWriterExtensionProvider;
import com.etl.writer.spi.WriterExtensionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Default writer registration resolver for non-Spring/manual construction.
 */
public final class DynamicWriterDefaults {

    private static final Logger logger = LoggerFactory.getLogger(DynamicWriterDefaults.class);
    private static final WriterExtensionProvider BUILT_IN_PROVIDER = new BuiltInWriterExtensionProvider();

    private DynamicWriterDefaults() {
    }

    public static List<DynamicWriter> defaultWriters() {
        return resolveWriters(discoverProviders());
    }

    static List<DynamicWriter> resolveWriters(List<WriterExtensionProvider> providers) {
        List<ExtensionConflictPolicy.Candidate<ModelFormat, DynamicWriter>> candidates = new ArrayList<>();
        for (WriterExtensionProvider provider : providers == null ? List.<WriterExtensionProvider>of() : providers) {
            for (DynamicWriter writer : provider.writers()) {
                if (writer == null) {
                    continue;
                }
                candidates.add(new ExtensionConflictPolicy.Candidate<>(
                        writer.getFormat(),
                        writer,
                        new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), provider.isOverride())
                ));
            }
        }

        return ExtensionConflictPolicy.resolve(candidates, new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(ModelFormat key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Writer format '{}' overridden by provider '{}' replacing provider '{}'.",
                        key, winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(ModelFormat key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override writer provider '{}' for format '{}' because override provider '{}' already registered.",
                        ignored.providerId(), key, winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(ModelFormat key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new FactoryException("Multiple writers registered for format: " + key
                        + " (providers: " + existing.providerId() + ", " + candidate.providerId() + ")"
                        + ". Set exactly one provider with isOverride=true to replace an existing writer.");
            }
        }).values().stream().toList();
    }

    private static List<WriterExtensionProvider> discoverProviders() {
        List<WriterExtensionProvider> providers = new ArrayList<>();
        providers.add(BUILT_IN_PROVIDER);

        ServiceLoader<WriterExtensionProvider> discovered = ServiceLoader.load(WriterExtensionProvider.class);
        for (WriterExtensionProvider provider : discovered) {
            if (provider.getClass().equals(BuiltInWriterExtensionProvider.class)) {
                continue;
            }
            providers.add(provider);
        }

        providers.sort(Comparator
                .comparingInt(WriterExtensionProvider::order)
                .thenComparing(WriterExtensionProvider::providerId)
                .thenComparing(provider -> provider.getClass().getName()));

        if (logger.isDebugEnabled()) {
            logger.debug("Discovered writer extension providers: {}", providers.stream()
                    .map(provider -> provider.providerId() + "(" + provider.getClass().getSimpleName() + ")")
                    .toList());
        }

        return List.copyOf(providers);
    }
}



