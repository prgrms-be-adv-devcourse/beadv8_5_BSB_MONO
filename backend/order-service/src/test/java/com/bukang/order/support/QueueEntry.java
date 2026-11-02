package com.bukang.order.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

/**
 * 테스트에서 신청서에 들어오는 단계 (PRO-249). 프론트처럼 대기열 입장 상태 API를 불러, 입장 중이면 끝 시각이 저장되게 한다
 * [결제하기]는 대기열에 다시 묻지 않고 이 저장값을 읽으므로, 주문을 만드는 테스트는 신청서를 쓰기 전에 이것을 부른다
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
