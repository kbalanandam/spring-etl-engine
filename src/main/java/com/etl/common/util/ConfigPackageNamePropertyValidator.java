package com.etl.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Validates that authored source/target YAML does not carry the deprecated packageName property.
 *
 * <p>This validator runs on raw YAML text before deserialization so packageName cannot slip
 * through silently when runtime mappers intentionally ignore unknown properties.</p>
 */
public final class ConfigPackageNamePropertyValidator {

    private ConfigPackageNamePropertyValidator() {
    }

    public static void requireNoSourcePackageNameProperties(ObjectMapper yamlMapper,
                                                            String yamlContent,
                                                            String configLocation,
                                                            String contextDescription,
                                                            String derivationOwnerDescription,
                                                            String derivedPackageName,
                                                            String derivationSourceDescription) throws IOException {
        requireNoPackageNameProperties(
                yamlMapper,
                yamlContent,
                configLocation,
                "sources",
                "sourceName",
                "source config",
                contextDescription,
                derivationOwnerDescription,
                derivedPackageName,
                derivationSourceDescription
        );
    }

    public static void requireNoTargetPackageNameProperties(ObjectMapper yamlMapper,
                                                            String yamlContent,
                                                            String configLocation,
                                                            String contextDescription,
                                                            String derivationOwnerDescription,
                                                            String derivedPackageName,
                                                            String derivationSourceDescription) throws IOException {
        requireNoPackageNameProperties(
                yamlMapper,
                yamlContent,
                configLocation,
                "targets",
                "targetName",
                "target config",
                contextDescription,
                derivationOwnerDescription,
                derivedPackageName,
                derivationSourceDescription
        );
    }

    private static void requireNoPackageNameProperties(ObjectMapper yamlMapper,
                                                       String yamlContent,
                                                       String configLocation,
                                                       String collectionProperty,
                                                       String logicalNameProperty,
                                                       String configType,
                                                       String contextDescription,
                                                       String derivationOwnerDescription,
                                                       String derivedPackageName,
                                                       String derivationSourceDescription) throws IOException {
        if (yamlContent == null || yamlContent.isBlank()) {
            return;
        }

        JsonNode root = yamlMapper.readTree(yamlContent);
        if (root == null) {
            return;
        }

        JsonNode configArray = root.get(collectionProperty);
        if (configArray == null || !configArray.isArray()) {
            return;
        }

        for (JsonNode configNode : configArray) {
            if (configNode == null || !configNode.has("packageName")) {
                continue;
            }

            JsonNode packageNode = configNode.get("packageName");
            String authoredPackageName = packageNode == null || packageNode.isNull()
                    ? ""
                    : packageNode.asText("");
            String logicalName = textValue(configNode.get(logicalNameProperty));

            throw new IllegalStateException(buildPackageNameNotAllowedMessage(
                    contextDescription,
                    configType,
                    logicalName,
                    configLocation,
                    authoredPackageName,
                    derivationOwnerDescription,
                    derivedPackageName,
                    derivationSourceDescription
            ));
        }
    }

    private static String buildPackageNameNotAllowedMessage(String contextDescription,
                                                            String configType,
                                                            String logicalName,
                                                            String configLocation,
                                                            String authoredPackageName,
                                                            String derivationOwnerDescription,
                                                            String derivedPackageName,
                                                            String derivationSourceDescription) {
        return defaultValue(contextDescription, "Runtime config")
                + " does not allow explicit packageName for " + defaultValue(configType, "config")
                + " '" + defaultValue(logicalName, "unnamed") + "' in " + defaultValue(configLocation, "selected config")
                + ": authored packageName='" + defaultValue(authoredPackageName, "")
                + "'. Remove packageName so " + defaultValue(derivationOwnerDescription, "runtime")
                + " derives generated package '" + defaultValue(derivedPackageName, "")
                + "' " + defaultValue(derivationSourceDescription, "internally") + ".";
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
