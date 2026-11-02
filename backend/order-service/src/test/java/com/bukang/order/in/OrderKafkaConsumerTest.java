package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.order.app.RaceCancelRefundUseCase;

import tools.jackson.databind.json.JsonMapper;

class OrderKafkaConsumerTest {
	private static final String PAYLOAD = "{\"raceId\":300,\"sellerId\":7,\"canceledAt\":\"2026-10-14T18:00:00\"}";

	private final RaceCancelRefundUseCase useCase = mock();
	private final OrderKafkaConsumer consumer =
		new OrderKafkaConsumer(useCase, new JsonConverter(JsonMapper.builder().build()));

	@Test
	@DisplayName("대회 취소 메시지(RaceCancelEvent)를 받으면 그 대회 ID로 일괄 환불한다")
	void refundsByRaceId() {
		given(useCase.refundAll(eq(300L), any(LocalDateTime.class)))
			.willReturn(new RaceCancelRefundUseCase.RefundResult(3, 0));

		consumer.handleRaceCancel(PAYLOAD);

		verify(useCase).refundAll(eq(300L), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("환불하지 못한 주문이 있으면 예외로 끝내 메시지를 다시 처리하게 한다")
	void throwsWhenSomeRefundsFailed() {
		given(useCase.refundAll(eq(300L), any(LocalDateTime.class)))
			.willReturn(new RaceCancelRefundUseCase.RefundResult(2, 1));

		assertThatThrownBy(() -> consumer.handleRaceCancel(PAYLOAD)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("깨진 JSON은 다시 해도 안 되므로 IllegalArgumentException으로 끝내 바로 DLT로 보낸다")
	void brokenJson() {
		assertThatThrownBy(() -> consumer.handleRaceCancel("{")).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(useCase);
	}
}
