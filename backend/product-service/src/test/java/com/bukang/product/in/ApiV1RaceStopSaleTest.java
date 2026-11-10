package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 판매 중지·다시 열기 API(POST /api/v1/product/races/{raceId}/stop, /resume)를 확인한다. 11/08 결정, docs/policy/questions.md
 * - 멈추고 다시 열 때마다 판매 상태가 바뀌고, 대기열에 대회 변경 메시지(queuing.race.changed)로 알린다
 * - 멈춘 대회에는 새 자리가 잡히지 않지만, 멈추기 전에 잡은 결제 대기 자리는 결제(판매)된다
 */
class ApiV1RaceStopSaleTest extends ProductIntegrationTest {
	private static final String RACES_PATH = "/api/v1/product/races/";

	@Test
	@DisplayName("판매 중인 대회를 멈추면 판매 중지, 다시 열면 판매 중이 되고, 대기열에 그 상태를 차례로 알린다")
	void stopAndResumeRace() throws Exception {
		// given
		RegisteredRace onSale = registerOnSale(race().build());
		int messagesBefore = raceChangedEvents().size();

		// when
		ResultActions stopped = postJson(RACES_PATH + onSale.raceId() + "/stop", SELLER_MEMBER_ID, null);
		ResultActions resumed = postJson(RACES_PATH + onSale.raceId() + "/resume", SELLER_MEMBER_ID, null);

		// then
		stopped.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회 판매를 멈췄습니다."))
			.andExpect(jsonPath("$.data.saleStatus").value("STOPPED"));
		resumed.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회 판매를 다시 열었습니다."))
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"));
		List<RaceChangedEvent> sent = raceChangedEvents();
		assertThat(sent.subList(messagesBefore, sent.size()))
			.extracting(RaceChangedEvent::raceId, RaceChangedEvent::saleStatus)
			.containsExactly(
				tuple(onSale.raceId(), RaceSaleStatus.STOPPED),
				tuple(onSale.raceId(), RaceSaleStatus.ON_SALE));
	}

	@Test
	@DisplayName("판매를 멈춘 대회에 새 자리 확보는 409 '판매를 잠시 멈춘 대회입니다.'이고, 멈추기 전에 잡은 결제 대기 자리는 판매된다")
	void rejectNewHoldButSellHeldSeatOfStoppedRace() throws Exception {
		// given
		RegisteredRace onSale = registerOnSale(race().build());
		hold(onSale.courseId(), 1).andExpect(jsonPath("$.data.held").value(true));
		postJson(RACES_PATH + onSale.raceId() + "/stop", SELLER_MEMBER_ID, null).andExpect(status().isOk());

		// when
		ResultActions newHold = hold(onSale.courseId(), 2);
		ResultActions heldSeatSell = sell(1);

		// then
		newHold.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("판매를 잠시 멈춘 대회입니다."));
		heldSeatSell.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.result").value("SOLD"));
		RaceCourse course = raceCourseRepository.findById(onSale.courseId()).orElseThrow();
		assertSoftly(softly -> {
			softly.assertThat(course.getIndividualReserved()).isZero();
			softly.assertThat(course.getIndividualSold()).isEqualTo(1);
		});
	}

	private List<RaceChangedEvent> raceChangedEvents() {
		return outboxPayloads(KafkaTopics.RACE_CHANGED).stream()
			.map(payload -> jsonConverter.fromJson(payload, RaceChangedEvent.class))
			.toList();
	}
}
