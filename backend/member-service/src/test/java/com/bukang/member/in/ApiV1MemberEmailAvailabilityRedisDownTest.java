package com.bukang.member.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.member.out.emailcheck.EmailCheckStore;

// Redis가 실패하면 횟수를 세지 못하므로 요청도 그대로 실패시킨다(막지 않고 통과시키지 않는다)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1MemberEmailAvailabilityRedisDownTest {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EmailCheckStore emailCheckStore;

	@Test
	@DisplayName("Redis 오류가 나면 이메일 확인은 500 INTERNAL_ERROR로 실패한다")
	void failsWhenRedisFails() throws Exception {
		when(emailCheckStore.tryAcquire(anyString(), anyInt(), any(Duration.class)))
			.thenThrow(new QueryTimeoutException("redis down"));

		mockMvc.perform(get("/api/v1/member/members/email-availability")
				.param("email", "runner1@crewrun.com").header("X-Real-IP", "203.0.113.7"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}
}
