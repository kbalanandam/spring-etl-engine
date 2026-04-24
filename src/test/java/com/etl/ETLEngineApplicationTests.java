package com.etl;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ETLEngineApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@MockitoBean
	private JobLauncher jobLauncher;

	@Test
	void contextLoads() {
		assertNotNull(applicationContext);
	}

}

