package com.etl.reader;

import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import com.etl.exception.FactoryException;
import com.etl.reader.spi.BuiltInReaderExtensionProvider;
import com.etl.reader.spi.ReaderExtensionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Default reader registration resolver for non-Spring/manual construction.
 */
public final class DynamicReaderDefaults {

    private static final Logger logger = LoggerFactory.getLogger(DynamicReaderDefaults.class);
    private static final ReaderExtensionProvider BUILT_IN_PROVIDER = new BuiltInReaderExtensionProvider();

    private DynamicReaderDefaults() {
    }

    public static List<DynamicReader<?>> defaultReaders() {
        return resolveReaders(discoverProviders());
    }

    static List<DynamicReader<?>> resolveReaders(List<ReaderExtensionProvider> providers) {
        List<ExtensionConflictPolicy.Candidate<ModelFormat, DynamicReader<?>>> candidates = new ArrayList<>();
        for (ReaderExtensionProvider provider : providers == null ? List.<ReaderExtensionProvider>of() : providers) {
            for (DynamicReader<?> reader : provider.readers()) {
                if (reader == null) {
                    continue;
                }
                candidates.add(new ExtensionConflictPolicy.Candidate<>(
                        reader.getFormat(),
                        reader,
                        new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), provider.isOverride())
                ));
            }
        }

        return ExtensionConflictPolicy.resolve(candidates, new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(ModelFormat key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Reader format '{}' overridden by provider '{}' replacing provider '{}'.",
                        key, winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(ModelFormat key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override reader provider '{}' for format '{}' because override provider '{}' already registered.",
                        ignored.providerId(), key, winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(ModelFormat key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new FactoryException("Multiple readers registered for format: " + key
                        + " (providers: " + existing.providerId() + ", " + candidate.providerId() + ")"
                        + ". Set exactly one provider with isOverride=true to replace an existing reader.");
            }
        }).values().stream().toList();
    }

    private static List<ReaderExtensionProvider> discoverProviders() {
        List<ReaderExtensionProvider> providers = new ArrayList<>();
        providers.add(BUILT_IN_PROVIDER);

        ServiceLoader<ReaderExtensionProvider> discovered = ServiceLoader.load(ReaderExtensionProvider.class);
        for (ReaderExtensionProvider provider : discovered) {
            if (provider.getClass().equals(BuiltInReaderExtensionProvider.class)) {
                continue;
            }
            providers.add(provider);
        }

        providers.sort(Comparator
                .comparingInt(ReaderExtensionProvider::order)
                .thenComparing(ReaderExtensionProvider::providerId)
                .thenComparing(provider -> provider.getClass().getName()));

        if (logger.isDebugEnabled()) {
            logger.debug("Discovered reader extension providers: {}", providers.stream()
                    .map(provider -> provider.providerId() + "(" + provider.getClass().getSimpleName() + ")")
                    .toList());
        }

        return List.copyOf(providers);
    }
}



