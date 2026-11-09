package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.order.app.CrewMemberLeftUseCase;
import com.bukang.order.app.OrderInboxUseCase;
import com.bukang.order.app.RaceCancelRefundUseCase;

import tools.jackson.databind.json.JsonMapper;

class OrderKafkaConsumerTest {
	private static final String PAYLOAD = "{\"raceId\":300,\"sellerId\":7,\"canceledAt\":\"2026-10-14T18:00:00\"}";

	private static final String CREW_LEFT_PAYLOAD = "{\"eventId\":\"3f1c\",\"crewId\":7,\"memberId\":2,"
		+ "\"reason\":\"%s\",\"joinedAt\":\"2026-11-01T09:00:00\",\"leftAt\":\"2026-11-04T12:00:00\"}";

	private static final String MESSAGE_ID = "product-outbox-3c7e1a9b-2f4d-4b8e-a6c1-9d0e5f7a2b42";

	private final RaceCancelRefundUseCase useCase = mock();
	private final OrderInboxUseCase inbox = mock();
	private final CrewMemberLeftUseCase crewMemberLeftUseCase = mock();
	private final OrderKafkaConsumer consumer = new OrderKafkaConsumer(useCase, inbox, crewMemberLeftUseCase,
		new JsonConverter(JsonMapper.builder().build()));

	@Test
	@DisplayName("대회 취소 메시지(RaceCancelEvent)를 받으면 그 대회 ID로 일괄 환불한다")
	void refundsByRaceId() {
		given(useCase.refundAll(eq(300L), any(LocalDateTime.class)))
			.willReturn(new RaceCancelRefundUseCase.RefundResult(3, 0));

		consumer.handleRaceCancel(PAYLOAD, MESSAGE_ID);

		verify(useCase).refundAll(eq(300L), any(LocalDateTime.class));
		verify(inbox)
			.record(eq(MESSAGE_ID), eq("order"), eq(KafkaTopics.ORDER_RACE_CANCEL), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("이미 처리한 메시지(같은 message-id)를 다시 받으면 환불 흐름을 돌지 않는다 (BUK-53)")
	void skipsProcessedMessage() {
		given(inbox.alreadyProcessed(MESSAGE_ID, "order", KafkaTopics.ORDER_RACE_CANCEL)).willReturn(true);

		consumer.handleRaceCancel(PAYLOAD, MESSAGE_ID);

		verifyNoInteractions(useCase);
	}

	@Test
	@DisplayName("환불하지 못한 주문이 있으면 예외로 끝내 메시지를 다시 처리하게 한다")
	void throwsWhenSomeRefundsFailed() {
		given(useCase.refundAll(eq(300L), any(LocalDateTime.class)))
			.willReturn(new RaceCancelRefundUseCase.RefundResult(2, 1));

		assertThatThrownBy(() -> consumer.handleRaceCancel(PAYLOAD, MESSAGE_ID))
			.isInstanceOf(IllegalStateException.class);
		// 남기지 않아야 다시 받았을 때 실패한 주문을 다시 환불한다
		verify(inbox, never()).record(any(), any(), any(), any());
	}

	@Test
	@DisplayName("깨진 JSON은 다시 해도 안 되므로 IllegalArgumentException으로 끝내 바로 DLT로 보낸다")
	void brokenJson() {
		assertThatThrownBy(() -> consumer.handleRaceCancel("{", MESSAGE_ID))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(useCase);
	}

	@Test
	@DisplayName("크루 떠남 메시지를 받으면 크루·회원·크루 가입 시각으로 파티에서 빼고 인박스에 남긴다. 모르는 이유 값도 처리한다")
	void crewMemberLeft() {
		consumer.handleCrewMemberLeft(CREW_LEFT_PAYLOAD.formatted("SOMETHING_NEW"), MESSAGE_ID);

		verify(crewMemberLeftUseCase).removeFromParties(eq(7L), eq(2L), eq(LocalDateTime.of(2026, 11, 1, 9, 0)),
			any(LocalDateTime.class));
		verify(inbox)
			.record(eq(MESSAGE_ID), eq("order"), eq(KafkaTopics.CREW_MEMBER_LEFT), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("이미 처리한 크루 떠남 메시지는 건너뛴다")
	void crewMemberLeftSkipsProcessed() {
		given(inbox.alreadyProcessed(MESSAGE_ID, "order", KafkaTopics.CREW_MEMBER_LEFT)).willReturn(true);

		consumer.handleCrewMemberLeft(CREW_LEFT_PAYLOAD.formatted("LEFT"), MESSAGE_ID);

		verifyNoInteractions(crewMemberLeftUseCase);
	}

	@Test
	@DisplayName("크루 가입 시각이나 회원 ID가 없는 크루 떠남 메시지는 누구를 뺄지 정할 수 없어 바로 DLT로 보낸다")
	void crewMemberLeftWithoutJoinedAt() {
		assertThatThrownBy(() -> consumer.handleCrewMemberLeft("{\"crewId\":7,\"memberId\":2}", MESSAGE_ID))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> consumer.handleCrewMemberLeft(
			"{\"crewId\":7,\"joinedAt\":\"2026-11-01T09:00:00\"}", MESSAGE_ID))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(crewMemberLeftUseCase);
	}
}
