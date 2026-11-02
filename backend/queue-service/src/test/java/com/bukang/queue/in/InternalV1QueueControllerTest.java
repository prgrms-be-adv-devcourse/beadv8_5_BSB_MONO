package com.bukang.queue.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.queue.support.QueueIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * 주문 서비스용 입장 확인 내부 API(GET /internal/v1/queue/races/{raceId}/racecourses/{raceCourseId})를
 * 실제 Redis 컨테이너로 확인한다.
 * Facade가 실제 지금으로 끝 시각을 비교하므로, active도 실제 지금을 기준으로 끝 시각을 잡아 Redis에 직접 넣는다.
 * 남은 시간이 정확한지와 Redis 장애 때의 답은 고정 시각으로 QueueAdmissionUseCaseTest가 본다.
 */
class InternalV1QueueControllerTest extends QueueIntegrationTest {
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final long MEMBER_ID = 7;
	private static final String ACTIVE_KEY = "race:1:racecourse:11:active";
	private static final String ADMISSION_URL = "/internal/v1/queue/races/{raceId}/racecourses/{raceCourseId}";
	// 활성 시간 10분 (ms)
	private static final long ACTIVE_MILLIS = 600_000;
	// QueueRedisRepository가 score(epoch ms)를 만들 때 쓰는 시간대
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("getAdmission: active 끝 시각이 10분 뒤인 회원은 입장 중이고, 그 끝 시각과 남은 시간(0보다 크고 10분 이하)을 받는다")
	void admittedWhenActive() throws Exception {
		// score는 ms 단위라, 응답의 끝 시각과 그대로 비교할 수 있게 ms 아래를 잘라 넣는다
		LocalDateTime activeUntil = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.MILLIS);
		redisTemplate.opsForZSet().add(ACTIVE_KEY, "7", epochMillis(activeUntil));

		String body = getAdmission(MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대기열에 입장 중입니다."))
			.andExpect(jsonPath("$.data.admitted").value(true))
			.andReturn().getResponse().getContentAsString();

		String admissionEndsAt = JsonPath.read(body, "$.data.admissionEndsAt");
		assertThat(LocalDateTime.parse(admissionEndsAt)).isEqualTo(activeUntil);
		Number remainingMillis = JsonPath.read(body, "$.data.remainingMillis");
		assertThat(remainingMillis.longValue()).isPositive().isLessThanOrEqualTo(ACTIVE_MILLIS);
	}

	@Test
	@DisplayName("getAdmission: active 끝 시각이 1분 전에 지난 회원은 스케줄러가 지우기 전이라도 입장 중이 아니다")
	void notAdmittedWhenActiveEnded() throws Exception {
		redisTemplate.opsForZSet().add(ACTIVE_KEY, "7", epochMillis(LocalDateTime.now().minusMinutes(1)));

		getAdmission(MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대기열에 입장 중이 아닙니다."))
			.andExpect(jsonPath("$.data.admitted").value(false))
			.andExpect(jsonPath("$.data.admissionEndsAt").value(nullValue()))
			.andExpect(jsonPath("$.data.remainingMillis").value(0));
	}

	@Test
	@DisplayName("getAdmission: active에 다른 회원(8)만 있고 회원 7은 없으면, 회원 7은 입장 중이 아니다")
	void notAdmittedWhenNotInQueue() throws Exception {
		redisTemplate.opsForZSet().add(ACTIVE_KEY, "8", epochMillis(LocalDateTime.now().plusMinutes(10)));

		getAdmission(MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대기열에 입장 중이 아닙니다."))
			.andExpect(jsonPath("$.data.admitted").value(false))
			.andExpect(jsonPath("$.data.admissionEndsAt").value(nullValue()))
			.andExpect(jsonPath("$.data.remainingMillis").value(0));
	}

	@Test
	@DisplayName("getAdmission: 회원 ID 헤더가 없으면 401이다")
	void rejectWithoutMemberIdHeader() throws Exception {
		mockMvc.perform(get(ADMISSION_URL, RACE_ID, RACE_COURSE_ID))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	@DisplayName("getAdmission: 대회 ID가 0이면 400이다")
	void rejectInvalidRaceId() throws Exception {
		mockMvc.perform(get(ADMISSION_URL, 0, RACE_COURSE_ID)
				.header(MemberIdHeader.NAME, MEMBER_ID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대회 ID는 0보다 커야 합니다."));
	}

	// 대회 1·종목 11의 입장 여부를 묻는다
	private ResultActions getAdmission(long memberId) throws Exception {
		return mockMvc.perform(get(ADMISSION_URL, RACE_ID, RACE_COURSE_ID)
			.header(MemberIdHeader.NAME, memberId));
	}

	// QueueRedisRepository와 같은 방식으로 score(epoch ms)를 만든다
	private static double epochMillis(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}
}
