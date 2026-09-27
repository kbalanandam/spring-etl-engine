package com.etl.integration;

import com.etl.ETLEngineApplication;
import com.etl.controlplane.ControlPlaneApiApplication;
import com.etl.runner.EtlJobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest(
        classes = ETLEngineApplication.class,
        properties = {
                "controlplane.triggers.persistence.mode=memory",
                "controlplane.runs.persistence.mode=memory",
                "controlplane.schedules.persistence.mode=memory"
        }
)
class EtlWorkerControlPlaneDisabledFallbackTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private JobLauncher jobLauncher;

    @Test
    void directEtlRuntimeStartsWhenControlPlanePersistenceIsDisabled() {
        assertNotNull(applicationContext);
        assertNotNull(applicationContext.getBean(EtlJobRunner.class));
        assertEquals(0, applicationContext.getBeanNamesForType(ControlPlaneApiApplication.class).length);
    }
}

