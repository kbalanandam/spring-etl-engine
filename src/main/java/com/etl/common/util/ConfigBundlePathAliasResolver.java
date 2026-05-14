package com.etl.common.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves compatibility aliases between the canonical config bundle folder name and
 * the legacy scenario-era folder name.
 * <p>
 * The checked-in runnable bundles now live under {@code config-jobs}, while explicit
 * runtime and generation entry points still accept legacy {@code config-scenarios}
 * paths for backward compatibility during the transition away from the old folder
 * name.
 * </p>
 *
 */
public final class ConfigBundlePathAliasResolver {

    public static final String PREFERRED_BUNDLE_FOLDER = "config-jobs";
    public static final String LEGACY_BUNDLE_FOLDER = "config-scenarios";

    private ConfigBundlePathAliasResolver() {
    }

    public static String resolveExistingPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return configuredPath;
        }
        return resolveExistingPath(Path.of(configuredPath.trim())).toString();
    }

    public static Path resolveExistingPath(Path configuredPath) {
        if (configuredPath == null) {
            return null;
        }

        Path normalized = configuredPath.normalize();
        if (Files.exists(normalized)) {
            return normalized;
        }

        Path legacyToPreferred = swapBundleFolder(normalized, LEGACY_BUNDLE_FOLDER, PREFERRED_BUNDLE_FOLDER);
        if (legacyToPreferred != null && Files.exists(legacyToPreferred)) {
            return legacyToPreferred.normalize();
        }

        Path preferredToLegacy = swapBundleFolder(normalized, PREFERRED_BUNDLE_FOLDER, LEGACY_BUNDLE_FOLDER);
        if (preferredToLegacy != null && Files.exists(preferredToLegacy)) {
            return preferredToLegacy.normalize();
        }

        return normalized;
    }

    public static String resolveExistingResourceName(ClassLoader classLoader, String resourceName) {
        if (resourceName == null || resourceName.isBlank() || classLoader == null) {
            return resourceName;
        }

        if (resourceExists(classLoader, resourceName)) {
            return resourceName;
        }

        String legacyToPreferred = swapBundleFolder(resourceName, LEGACY_BUNDLE_FOLDER, PREFERRED_BUNDLE_FOLDER);
        if (legacyToPreferred != null && resourceExists(classLoader, legacyToPreferred)) {
            return legacyToPreferred;
        }

        String preferredToLegacy = swapBundleFolder(resourceName, PREFERRED_BUNDLE_FOLDER, LEGACY_BUNDLE_FOLDER);
        if (preferredToLegacy != null && resourceExists(classLoader, preferredToLegacy)) {
            return preferredToLegacy;
        }

        return resourceName;
    }

    public static Path resolveBundleRoot(Path resourceRoot) {
        Path preferred = resourceRoot.resolve(PREFERRED_BUNDLE_FOLDER);
        if (Files.isDirectory(preferred)) {
            return preferred;
        }

        Path legacy = resourceRoot.resolve(LEGACY_BUNDLE_FOLDER);
        if (Files.isDirectory(legacy)) {
            return legacy;
        }

        return preferred;
    }

    private static boolean resourceExists(ClassLoader classLoader, String resourceName) {
        URL resource = classLoader.getResource(resourceName);
        return resource != null;
    }

    private static Path swapBundleFolder(Path path, String from, String to) {
        List<String> segments = new ArrayList<>();
        boolean replaced = false;
        for (Path segment : path) {
            String value = segment.toString();
            if (!replaced && from.equals(value)) {
                segments.add(to);
                replaced = true;
            } else {
                segments.add(value);
            }
        }

        if (!replaced || segments.isEmpty()) {
            return null;
        }

        Path resolved = Path.of(segments.get(0), segments.subList(1, segments.size()).toArray(String[]::new));
        if (path.isAbsolute()) {
            Path root = path.getRoot();
            return root == null ? resolved : root.resolve(resolved);
        }
        return resolved;
    }

    private static String swapBundleFolder(String value, String from, String to) {
        String slashValue = value.replace('\\', '/');
        if (slashValue.equals(from)) {
            return to;
        }
        String fromToken = "/" + from + "/";
        String toToken = "/" + to + "/";
        if (slashValue.contains(fromToken)) {
            return slashValue.replace(fromToken, toToken);
        }
        if (slashValue.startsWith(from + "/")) {
            return to + slashValue.substring(from.length());
        }
        return null;
    }
}


