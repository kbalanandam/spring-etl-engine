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
                "controlplane.triggers.persistence.mode=jpa",
                "controlplane.runs.persistence.mode=jpa",
                "controlplane.schedules.persistence.mode=jpa",
                "controlplane.db.vendor=mysql",
                "controlplane.db.url=jdbc:mysql://127.0.0.1:65000/etl_controlplane_unavailable",
                "controlplane.db.username=invalid",
                "controlplane.db.password=invalid",
                "controlplane.db.driver-class-name=com.mysql.cj.jdbc.Driver"
        }
)
class EtlWorkerControlPlaneIndependenceTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private JobLauncher jobLauncher;

    @Test
    void directEtlRuntimeStartsEvenWhenControlPlaneDbSettingsPointToUnavailableEndpoint() {
        assertNotNull(applicationContext);
        assertNotNull(applicationContext.getBean(EtlJobRunner.class));
        assertEquals(0, applicationContext.getBeanNamesForType(ControlPlaneApiApplication.class).length);
    }
}


