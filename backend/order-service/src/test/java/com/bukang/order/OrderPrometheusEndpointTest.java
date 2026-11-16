package com.bukang.order;

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

// 결제 잠금 부하 측정 지표: common observability.yaml이 prometheus 엔드포인트를 기본으로 열어,
// 측정 때 쓰는 JVM · 커넥션 풀 지표가 나오는지 본다
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMetrics
@AutoConfigureMockMvc
class OrderPrometheusEndpointTest {
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
}
