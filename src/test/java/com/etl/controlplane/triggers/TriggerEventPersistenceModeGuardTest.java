package com.etl.controlplane.triggers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerEventPersistenceModeGuardTest {

	@TempDir
	Path tempDir;

	@Test
	void failsFastWhenModeSwitchDetectedWithoutExplicitOverride() throws Exception {
		Path marker = tempDir.resolve("trigger-mode.marker");
		Files.writeString(marker, "jdbc\n");

		TriggerEventPersistenceModeGuard guard = new TriggerEventPersistenceModeGuard("memory", marker.toString(), false);
		IllegalStateException error = assertThrows(IllegalStateException.class, guard::validateModeSwitch);

		assertTrue(error.getMessage().contains("allow-mode-switch=true"));
	}

	@Test
	void allowsModeSwitchWhenOverrideIsExplicit() throws Exception {
		Path marker = tempDir.resolve("trigger-mode.marker");
		Files.writeString(marker, "memory\n");

		TriggerEventPersistenceModeGuard guard = new TriggerEventPersistenceModeGuard("jdbc", marker.toString(), true);
		guard.validateModeSwitch();

		assertEquals("jdbc", Files.readString(marker).trim());
	}

	@Test
	void writesMarkerOnFirstStartup() throws Exception {
		Path marker = tempDir.resolve("nested/path/trigger-mode.marker");
		TriggerEventPersistenceModeGuard guard = new TriggerEventPersistenceModeGuard("memory", marker.toString(), false);
		guard.validateModeSwitch();

		assertEquals("memory", Files.readString(marker).trim());
	}
}


