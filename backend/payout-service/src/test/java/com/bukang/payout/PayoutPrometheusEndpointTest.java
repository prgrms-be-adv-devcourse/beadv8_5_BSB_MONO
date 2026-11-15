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

// 부하 테스트 지표: prometheus 엔드포인트를 열면 JVM · 커넥션 풀 지표가 나온다
// 공통 설정(common의 observability.yaml)이 모든 프로필에서 열므로, 닫혀 있는 경우는 따로 확인하지 않는다
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
}
