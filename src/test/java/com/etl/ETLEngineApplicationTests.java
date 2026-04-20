package com.etl;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ETLEngineApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@MockBean
	private JobLauncher jobLauncher;

	@Test
	void contextLoads() {
		assertNotNull(applicationContext);
	}

}

