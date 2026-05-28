package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleService;
import com.etl.controlplane.schedules.ScheduleView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class ScheduleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ScheduleService scheduleService;

	@Test
	void listsSchedulesWithDefaultLimit() throws Exception {
		when(scheduleService.list(eq(25))).thenReturn(List.of(schedule("sch-1", "daily-customers")));

		mockMvc.perform(get("/api/v1/schedules"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].scheduleId").value("sch-1"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(25));

		verify(scheduleService).list(eq(25));
	}

	@Test
	void returnsScheduleById() throws Exception {
		when(scheduleService.findByScheduleId(eq("sch-1"))).thenReturn(Optional.of(schedule("sch-1", "daily-customers")));

		mockMvc.perform(get("/api/v1/schedules/sch-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scheduleKey").value("daily-customers"));
	}

	@Test
	void createsSchedule() throws Exception {
		when(scheduleService.createSchedule(eq("daily-customers"), eq("customer-load"), eq("0 0 * * *"), eq("UTC"), eq(true), eq("daily")))
				.thenReturn(schedule("sch-1", "daily-customers"));

		mockMvc.perform(post("/api/v1/schedules")
						.contentType("application/json")
						.content("{\"scheduleKey\":\"daily-customers\",\"selectedJobKey\":\"customer-load\",\"expression\":\"0 0 * * *\",\"timezone\":\"UTC\",\"enabled\":true,\"description\":\"daily\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.scheduleId").value("sch-1"));
	}

	@Test
	void updatesSchedule() throws Exception {
		when(scheduleService.updateSchedule(eq("sch-1"), eq("customer-load"), eq("0 15 * * *"), eq("UTC"), eq(true), eq("updated")))
				.thenReturn(Optional.of(schedule("sch-1", "daily-customers")));

		mockMvc.perform(put("/api/v1/schedules/sch-1")
						.contentType("application/json")
						.content("{\"selectedJobKey\":\"customer-load\",\"expression\":\"0 15 * * *\",\"timezone\":\"UTC\",\"enabled\":true,\"description\":\"updated\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scheduleId").value("sch-1"));
	}

	@Test
	void performsStateChange() throws Exception {
		when(scheduleService.pause(eq("sch-1"))).thenReturn(Optional.of(new ScheduleView(
				"sch-1", "daily-customers", "customer-load", "0 0 * * *", "UTC", true, true, "daily",
				LocalDateTime.parse("2026-05-28T08:00:00"), LocalDateTime.parse("2026-05-28T09:00:00"), null
		)));

		mockMvc.perform(post("/api/v1/schedules/sch-1:pause"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scheduleId").value("sch-1"))
				.andExpect(jsonPath("$.paused").value(true));
	}

	private ScheduleView schedule(String id, String key) {
		return new ScheduleView(
				id,
				key,
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"daily",
				LocalDateTime.parse("2026-05-28T08:00:00"),
				LocalDateTime.parse("2026-05-28T09:00:00"),
				null
		);
	}
}


