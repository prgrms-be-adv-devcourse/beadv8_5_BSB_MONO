package com.bukang.payout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 공통 설정(common의 observability.yaml)은 모든 프로필에서 health·prometheus만 연다
// 설정 값을 덮어쓰지 않고 띄워서, 공통 설정 그대로 열리고 닫히는지 확인한다
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMetrics
@AutoConfigureMockMvc
class PayoutPrometheusEndpointTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("JVM 메모리 · HikariCP 지표를 Prometheus 형식으로 내보낸다")
	void exportsMetrics() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")))
			.andExpect(content().string(Matchers.containsString("hikaricp_connections")));
	}

	@Test
	@DisplayName("env · beans처럼 설정값이 보이는 엔드포인트는 열지 않는다")
	void hidesConfigEndpoints() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
		mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
	}
}
