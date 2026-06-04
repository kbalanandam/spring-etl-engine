package com.etl.controlplane.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc
@ActiveProfiles("testcp")
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class SystemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsHealth() throws Exception {
		mockMvc.perform(get("/api/v1/system/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void returnsSystemInfo() throws Exception {
		mockMvc.perform(get("/api/v1/system/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.service").value("spring-etl-engine-control-plane"))
				.andExpect(jsonPath("$.profile").value("testcp"))
				.andExpect(jsonPath("$.javaVersion").exists())
				.andExpect(jsonPath("$.schedulerEnabled").value(false))
				.andExpect(jsonPath("$.schedulerMissedRunPolicy").value("SKIP"))
				.andExpect(jsonPath("$.schedulerOverlapPolicy").value("ALLOW"));
	}
}


