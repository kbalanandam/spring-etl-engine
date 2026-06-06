package com.etl.controlplane.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperatorUiController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class OperatorUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsOperatorRootToStaticShell() throws Exception {
        mockMvc.perform(get("/operator"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operator/index.html?v=20260606-jobs-pagination"));
    }

    @Test
    void redirectsOperatorRootWithTrailingSlashToStaticShell() throws Exception {
        mockMvc.perform(get("/operator/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operator/index.html?v=20260606-jobs-pagination"));
    }

    @Test
    void redirectsRunDeepLinkToHashRoutePlaceholder() throws Exception {
        mockMvc.perform(get("/operator/runs/901"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operator/index.html?v=20260606-jobs-pagination#/runs/901"));
    }

    @Test
    void redirectsJobDeepLinkToHashRoutePlaceholder() throws Exception {
        mockMvc.perform(get("/operator/jobs/customer-load"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operator/index.html?v=20260606-jobs-pagination#/jobs/customer-load"));
    }

    @Test
    void redirectsJobConfigDeepLinkToHashRoutePlaceholder() throws Exception {
        mockMvc.perform(get("/operator/jobs/customer-load/config"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/operator/index.html?v=20260606-jobs-pagination#/jobs/customer-load/config"));
    }
}



