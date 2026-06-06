package com.etl.controlplane.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the monitoring-first operator UI shell entry point.
 */
@Controller
public class OperatorUiController {

      private static final String UI_VERSION = "20260606-jobs-step-no-heading";

    @GetMapping({"/operator", "/operator/"})
    public String operatorHome() {
        return "redirect:/operator/index.html?v=" + UI_VERSION;
    }

    @GetMapping("/operator/runs/{jobExecutionId}")
    public String runDetailPlaceholder(@PathVariable long jobExecutionId) {
        return "redirect:/operator/index.html?v=" + UI_VERSION + "#/runs/" + jobExecutionId;
    }

    @GetMapping("/operator/jobs/{jobKey}")
    public String jobDetailPlaceholder(@PathVariable String jobKey) {
        return "redirect:/operator/index.html?v=" + UI_VERSION + "#/jobs/" + jobKey;
    }

      @GetMapping("/operator/jobs/{jobKey}/config")
      public String jobConfigPlaceholder(@PathVariable String jobKey) {
        return "redirect:/operator/index.html?v=" + UI_VERSION + "#/jobs/" + jobKey + "/config";
      }
}



