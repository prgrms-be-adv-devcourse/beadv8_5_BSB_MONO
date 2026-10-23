package com.bukang.product.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.product.domain.OutboxStatus;
import com.bukang.product.domain.ProductOutboxEvent;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 아웃박스 저장(ProductOutboxAppendUseCase)과 릴레이(ProductFacade.relayOutbox)를 확인한다.
 * - 저장은 대회 저장 트랜잭션 안에서만 할 수 있고, 그 트랜잭션이 롤백되면 행도 남지 않는다
 * - 릴레이는 미발행 행을 만든 순서대로 보내고, 브로커가 받으면 SENT로 바꾼다
 * - 보내지 못하면 재시도 횟수를 올리고 멈추며(뒤의 행은 보내지 않는다), MAX_RETRY번 실패하면 FAILED로 바꾼다
 * KafkaTemplate은 ProductIntegrationTest의 spy라 브로커 없이 바로 성공을 돌려준다. 실패는 토픽별로 실패한 결과를 돌려주게 바꿔 만든다.
 */
class ProductOutboxRelayTest extends ProductIntegrationTest {
	private static final int RACE_ID = 101;
	private static final int OTHER_RACE_ID = 102;

	@Autowired
	private ProductOutboxAppendUseCase productOutboxAppendUseCase;

	@Test
	@DisplayName("대회 저장 트랜잭션이 롤백되면 아웃박스 행도 남지 않는다")
	void rollbackWithBusinessTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			productOutboxAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID));
			status.setRollbackOnly();
		});

		assertThat(productOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("트랜잭션 밖에서 아웃박스에 저장하면 예외가 나고 행이 남지 않는다 (MANDATORY)")
	void rejectAppendOutsideTransaction() {
		assertThatThrownBy(() ->
			productOutboxAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID)))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(productOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("미발행 행을 남긴 순서대로 토픽·key(대회 ID)·본문 그대로 보내고, SENT와 보낸 시각을 남긴다")
	void relayPendingEventsInOrder() {
		append(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.ORDER_RACE_CANCEL, OTHER_RACE_ID);

		int sent = productFacade.relayOutbox();

		assertThat(sent).isEqualTo(2);
		InOrder order = inOrder(kafka());
		order.verify(kafka()).send(KafkaTopics.RACE_CHANGED, "101", jsonConverter.toJson(payload(RACE_ID)));
		order.verify(kafka()).send(KafkaTopics.ORDER_RACE_CANCEL, "102", jsonConverter.toJson(payload(OTHER_RACE_ID)));
		assertThat(allEvents()).allSatisfy(event -> {
			assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
			assertThat(event.getSentDate()).isNotNull();
			assertThat(event.getRetryCount()).isZero();
		});
	}

	@Test
	@DisplayName("이미 보낸(SENT) 행은 다음 릴레이에서 다시 보내지 않는다")
	void skipSentEvents() {
		append(KafkaTopics.RACE_CHANGED, RACE_ID);

		productFacade.relayOutbox();
		int sentAgain = productFacade.relayOutbox();

		assertThat(sentAgain).isZero();
		verify(kafka(), times(1)).send(eq(KafkaTopics.RACE_CHANGED), anyString(), anyString());
	}

	@Test
	@DisplayName("보내지 못하면 재시도 횟수를 1 올리고 PENDING으로 두며, 순서를 지키려고 뒤의 행은 보내지 않는다")
	void stopOnSendFailure() {
		failSendTo(KafkaTopics.RACE_CHANGED);
		append(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.ORDER_RACE_CANCEL, OTHER_RACE_ID);

		int sent = productFacade.relayOutbox();

		assertThat(sent).isZero();
		List<ProductOutboxEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(0).getRetryCount()).isEqualTo(1);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.PENDING);
		verify(kafka(), never()).send(eq(KafkaTopics.ORDER_RACE_CANCEL), anyString(), anyString());
	}

	@Test
	@DisplayName("MAX_RETRY번 보내지 못하면 FAILED로 바꾸고, 다음 릴레이부터는 그 행을 건너뛰고 뒤의 행을 보낸다")
	void markFailedAfterMaxRetry() {
		failSendTo(KafkaTopics.RACE_CHANGED);
		append(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.ORDER_RACE_CANCEL, OTHER_RACE_ID);

		for (int i = 0; i < ProductOutboxRelayUseCase.MAX_RETRY; i++) {
			productFacade.relayOutbox();
		}
		int sent = productFacade.relayOutbox();

		assertThat(sent).isEqualTo(1);
		List<ProductOutboxEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(events.get(0).getRetryCount()).isEqualTo(ProductOutboxRelayUseCase.MAX_RETRY);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.SENT);
		verify(kafka(), times(ProductOutboxRelayUseCase.MAX_RETRY))
			.send(eq(KafkaTopics.RACE_CHANGED), anyString(), anyString());
	}

	private void append(String topic, int raceId) {
		transactionTemplate.executeWithoutResult(status ->
			productOutboxAppendUseCase.appendRaceEvent(topic, raceId, payload(raceId)));
	}

	private static Map<String, Object> payload(int raceId) {
		return Map.of("raceId", raceId);
	}

	// 이 토픽으로 보내면 브로커가 거절한 것처럼 실패한 결과를 돌려준다
	private void failSendTo(String topic) {
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 응답 없음")))
			.when(kafka()).send(eq(topic), anyString(), anyString());
	}

	private List<ProductOutboxEvent> allEvents() {
		return productOutboxEventRepository.findAll().stream()
			.sorted((left, right) -> Integer.compare(left.getId(), right.getId()))
			.toList();
	}
}
