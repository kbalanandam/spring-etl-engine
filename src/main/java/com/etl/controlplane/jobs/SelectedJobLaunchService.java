package com.etl.controlplane.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Launches selected-job ETL runs as a separate worker process.
 */
@Service
public class SelectedJobLaunchService {

	private static final Logger log = LoggerFactory.getLogger(SelectedJobLaunchService.class);

	private final JobBundleReadModelService jobBundleReadModelService;
	private final boolean launchEnabled;

	@Autowired
	public SelectedJobLaunchService(JobBundleReadModelService jobBundleReadModelService,
	                                @Value("${controlplane.job-launch.enabled:false}") boolean launchEnabled) {
		this.jobBundleReadModelService = jobBundleReadModelService;
		this.launchEnabled = launchEnabled;
	}

	public LaunchResult launchSelectedJob(String selectedJobKey, String triggerOrigin, String scheduleId) {
		if (!launchEnabled) {
			return LaunchResult.skipped("Worker launch is disabled by controlplane.job-launch.enabled=false.");
		}

		String normalizedJobKey = normalize(selectedJobKey);
		if (normalizedJobKey.isBlank()) {
			return LaunchResult.skipped("Selected job key is blank.");
		}

		var bundle = jobBundleReadModelService.findBundle(normalizedJobKey);
		if (bundle.isEmpty()) {
			return LaunchResult.skipped("Selected job bundle was not found.");
		}

		Path jobConfigPath = Path.of(bundle.get().jobConfigPath()).normalize();
		if (!Files.isRegularFile(jobConfigPath)) {
			return LaunchResult.skipped("Selected job-config.yaml is missing at " + jobConfigPath + ".");
		}

		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		String classPath = normalize(System.getProperty("java.class.path"));
		if (classPath.isBlank()) {
			return LaunchResult.skipped("Classpath is unavailable for worker process launch.");
		}

		ProcessBuilder processBuilder = new ProcessBuilder(
				javaExecutable,
				"-Detl.config.job=" + jobConfigPath,
				"-Detl.config.allow-demo-fallback=false",
				"-cp",
				classPath,
				"com.etl.ETLEngineApplication");
		processBuilder.redirectErrorStream(true);
		processBuilder.inheritIO();

		String normalizedOrigin = normalize(triggerOrigin).toUpperCase();
		String normalizedScheduleId = normalize(scheduleId);
		try {
			Process process = processBuilder.start();
			long pid = process.pid();
			log.info("CONTROLPLANE_LAUNCH event=launch_started triggerOrigin={} scheduleId={} selectedJobKey={} pid={} jobConfigPath={}",
					normalizedOrigin,
					normalizedScheduleId,
					normalizedJobKey,
					pid,
					jobConfigPath);
			Thread completionWatcher = new Thread(
					() -> waitForLaunchCompletion(process, normalizedJobKey, normalizedOrigin, normalizedScheduleId),
					"controlplane-launch-wait-" + normalizedJobKey.replaceAll("[^a-zA-Z0-9_-]", "-"));
			completionWatcher.setDaemon(true);
			completionWatcher.start();
			return LaunchResult.started(pid, jobConfigPath.toString());
		} catch (IOException ex) {
			log.error("CONTROLPLANE_LAUNCH event=launch_failed triggerOrigin={} scheduleId={} selectedJobKey={} reason=process_start_failed message={}",
					normalizedOrigin,
					normalizedScheduleId,
					normalizedJobKey,
					ex.getMessage(),
					ex);
			return LaunchResult.skipped("Worker process start failed: " + ex.getMessage());
		}
	}

	private void waitForLaunchCompletion(Process process,
	                                     String selectedJobKey,
	                                     String triggerOrigin,
	                                     String scheduleId) {
		try {
			int exitCode = process.waitFor();
			log.info("CONTROLPLANE_LAUNCH event=launch_finished triggerOrigin={} scheduleId={} selectedJobKey={} pid={} exitCode={}",
					triggerOrigin,
					scheduleId,
					selectedJobKey,
					process.pid(),
					exitCode);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("CONTROLPLANE_LAUNCH event=launch_wait_interrupted triggerOrigin={} scheduleId={} selectedJobKey={} pid={}",
					triggerOrigin,
					scheduleId,
					selectedJobKey,
					process.pid());
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	public record LaunchResult(boolean started, String message) {
		static LaunchResult started(long pid, String jobConfigPath) {
			return new LaunchResult(true, "Worker launch started [pid=" + pid + "] jobConfigPath='" + jobConfigPath + "'.");
		}

		static LaunchResult skipped(String message) {
			return new LaunchResult(false, message == null ? "Worker launch skipped." : message.trim());
		}
	}
}

