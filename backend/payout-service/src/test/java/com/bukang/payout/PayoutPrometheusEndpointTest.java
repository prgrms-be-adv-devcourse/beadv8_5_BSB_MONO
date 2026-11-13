package com.bukang.payout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 부하 테스트 지표: prometheus 엔드포인트를 열면 JVM · 커넥션 풀 지표가 나오고, 열지 않으면(prod 기본값) 없다
class PayoutPrometheusEndpointTest {
	@Nested
	@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,prometheus")
	@ActiveProfiles("test")
	@AutoConfigureMetrics
	@AutoConfigureMockMvc
	class Exposed {
		@Autowired
		private MockMvc mockMvc;

		@Test
		@DisplayName("dev처럼 열면 JVM 메모리 · HikariCP 지표를 Prometheus 형식으로 내보낸다")
		void exportsMetrics() throws Exception {
			mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")))
				.andExpect(content().string(Matchers.containsString("hikaricp_connections")));
		}
	}

	@Nested
	@SpringBootTest
	@ActiveProfiles("test")
	@AutoConfigureMetrics
	@AutoConfigureMockMvc
	class NotExposed {
		@Autowired
		private MockMvc mockMvc;

		@Test
		@DisplayName("열지 않으면(prod 기본값) prometheus 엔드포인트가 없다")
		void hidden() throws Exception {
			mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isNotFound());
		}
	}
}
