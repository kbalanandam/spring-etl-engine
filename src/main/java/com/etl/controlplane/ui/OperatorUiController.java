package com.etl.controlplane.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the monitoring-first operator UI shell entry point.
 */
@Controller
public class OperatorUiController {

    @GetMapping({"/operator", "/operator/"})
    public String operatorHome() {
        return "redirect:/operator/index.html";
    }
}

