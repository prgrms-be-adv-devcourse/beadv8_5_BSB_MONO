package com.bukang.order.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

/**
 * 테스트에서 신청서에 들어오는 단계 (PRO-249). 프론트처럼 대기열 입장 상태 API를 부른다
 * 주문은 끝 시각을 저장하지 않고 [결제하기]가 대기열에 다시 묻는다 (CRU-53). 프론트 흐름을 따라 남겨 둔다
 * 대회 ID는 가짜·스텁 대기열이 쓰지 않아 아무 양수나 보낸다
 */
public final class QueueEntry {
	private static final long ANY_RACE_ID = 1;

	private QueueEntry() {
	}

	public static void enter(MockMvc mockMvc, long memberId, long raceCourseId) throws Exception {
		mockMvc.perform(get("/api/v1/order/queue/admission")
				.header("X-Member-Id", memberId)
				.param("raceId", String.valueOf(ANY_RACE_ID))
				.param("raceCourseId", String.valueOf(raceCourseId)))
			.andExpect(status().isOk());
	}
}
