package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import com.etl.controlplane.jobs.SelectedJobLaunchService;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import com.etl.controlplane.triggers.TriggerEventView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobBundleControllerTriggerNowUnitTest {

    @Mock
    private JobBundleReadModelService jobBundleReadModelService;

    @Mock
    private RunSummaryReadModelService runSummaryReadModelService;

    @Mock
    private TriggerEventRegistry triggerEventRegistry;

    @Mock
    private SelectedJobLaunchService selectedJobLaunchService;

    @Test
    void suppressesDuplicateInsideWindowUsingInjectedClock() {
        Instant now = Instant.parse("2026-06-24T09:15:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        JobBundleController controller = new JobBundleController(
                jobBundleReadModelService,
                runSummaryReadModelService,
                triggerEventRegistry,
                selectedJobLaunchService,
                fixedClock
        );

        when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(sampleBundle()));
        when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(5))).thenReturn(List.of(
                new TriggerEventView(
                        "te-existing",
                        "customer-load",
                        "ACCEPTED",
                        "manual_operator_request",
                        "operator-ui",
                        now.minusSeconds(3),
                        null,
                        "accepted"
                )
        ));

        var response = controller.triggerNow("customer-load", new TriggerNowRequest("manual_operator_request", "operator-ui"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        TriggerNowDecisionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("DUPLICATE_SUPPRESSED", body.decisionStatus());
        assertEquals("te-existing", body.triggerEventId());
        verify(triggerEventRegistry, never()).recordAccepted(anyString(), anyString(), anyString(), anyString());
        verify(selectedJobLaunchService, never()).launchSelectedJob(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void acceptsTriggerOutsideWindowUsingInjectedClock() {
        Instant now = Instant.parse("2026-06-24T09:15:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        JobBundleController controller = new JobBundleController(
                jobBundleReadModelService,
                runSummaryReadModelService,
                triggerEventRegistry,
                selectedJobLaunchService,
                fixedClock
        );

        when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(sampleBundle()));
        when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(5))).thenReturn(List.of(
                new TriggerEventView(
                        "te-old",
                        "customer-load",
                        "ACCEPTED",
                        "manual_operator_request",
                        "operator-ui",
                        now.minusSeconds(6),
                        null,
                        "accepted"
                )
        ));
        when(triggerEventRegistry.recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator-ui"), anyString()))
                .thenReturn(new TriggerEventView(
                        "te-new",
                        "customer-load",
                        "ACCEPTED",
                        "manual_operator_request",
                        "operator-ui",
                        now,
                        null,
                        "accepted"
                ));
        when(selectedJobLaunchService.launchSelectedJob(eq("customer-load"), eq("MANUAL"), eq(null), eq("te-new")))
                .thenReturn(new SelectedJobLaunchService.LaunchResult(true, "Worker launch started [pid=5555]."));

        var response = controller.triggerNow("customer-load", new TriggerNowRequest("manual_operator_request", "operator-ui"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        TriggerNowDecisionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCEPTED", body.decisionStatus());
        assertEquals("te-new", body.triggerEventId());
        verify(triggerEventRegistry).recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator-ui"), anyString());
        verify(selectedJobLaunchService).launchSelectedJob(eq("customer-load"), eq("MANUAL"), eq(null), eq("te-new"));
    }

    @Test
    void returnsLaunchSkippedWhenTriggerAcceptedButWorkerDidNotStart() {
        Instant now = Instant.parse("2026-06-24T09:15:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        JobBundleController controller = new JobBundleController(
                jobBundleReadModelService,
                runSummaryReadModelService,
                triggerEventRegistry,
                selectedJobLaunchService,
                fixedClock
        );

        when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(sampleBundle()));
        when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(5))).thenReturn(List.of());
        when(triggerEventRegistry.recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator-ui"), anyString()))
                .thenReturn(new TriggerEventView(
                        "te-new",
                        "customer-load",
                        "ACCEPTED",
                        "manual_operator_request",
                        "operator-ui",
                        now,
                        null,
                        "accepted"
                ));
        when(selectedJobLaunchService.launchSelectedJob(eq("customer-load"), eq("MANUAL"), eq(null), eq("te-new")))
                .thenReturn(new SelectedJobLaunchService.LaunchResult(false, "Worker launch skipped because an execution is already running."));

        var response = controller.triggerNow("customer-load", new TriggerNowRequest("manual_operator_request", "operator-ui"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        TriggerNowDecisionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("LAUNCH_SKIPPED", body.decisionStatus());
        assertEquals("te-new", body.triggerEventId());
        verify(triggerEventRegistry).recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator-ui"), anyString());
        verify(selectedJobLaunchService).launchSelectedJob(eq("customer-load"), eq("MANUAL"), eq(null), eq("te-new"));
    }

    @Test
    void continuesLaunchWhenTriggerRegistryIsUnavailable() {
        Instant now = Instant.parse("2026-06-24T09:15:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        JobBundleController controller = new JobBundleController(
                jobBundleReadModelService,
                runSummaryReadModelService,
                triggerEventRegistry,
                selectedJobLaunchService,
                fixedClock
        );

        when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(sampleBundle()));
        when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(5))).thenThrow(new IllegalStateException("registry unavailable"));
        when(triggerEventRegistry.recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator-ui"), anyString()))
                .thenThrow(new IllegalStateException("write unavailable"));
        when(selectedJobLaunchService.launchSelectedJob(eq("customer-load"), eq("MANUAL"), isNull(), isNull()))
                .thenReturn(new SelectedJobLaunchService.LaunchResult(true, "Worker launch started [pid=5555]."));

        var response = controller.triggerNow("customer-load", new TriggerNowRequest("manual_operator_request", "operator-ui"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        TriggerNowDecisionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCEPTED", body.decisionStatus());
        assertEquals(null, body.triggerEventId());
        verify(selectedJobLaunchService).launchSelectedJob(eq("customer-load"), eq("MANUAL"), isNull(), isNull());
    }

    private JobBundleSummaryView sampleBundle() {
        return new JobBundleSummaryView(
                "customer-load",
                "Customer Load",
                "src/main/resources/config-jobs/customer-load/job-config.yaml",
                "READY",
                List.of()
        );
    }
}
