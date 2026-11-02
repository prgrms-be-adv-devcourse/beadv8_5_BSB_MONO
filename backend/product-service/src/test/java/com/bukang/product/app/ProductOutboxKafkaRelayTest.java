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

import java.time.Duration;
import java.time.LocalDateTime;
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
import com.bukang.product.domain.ProductOutboxKafkaEvent;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 아웃박스 저장(ProductOutboxKafkaAppendUseCase)과 릴레이(ProductFacade.relayKafkaOutbox)를 확인한다.
 * - 저장은 대회 저장 트랜잭션 안에서만 할 수 있고, 그 트랜잭션이 롤백되면 행도 남지 않는다
 * - 릴레이는 미발행 행을 만든 순서대로 보내고, 브로커가 받으면 SENT로 바꾼다
 * - 보내지 못하면 그 토픽·대회(메시지 key)만 막고, 다른 토픽이나 다른 대회는 계속 보낸다
 * - 재시도 간격은 1초부터 두 배씩 늘리고, MAX_RETRY번 실패하면 SEND_FAILED로 바꾼다
 * KafkaTemplate은 ProductIntegrationTest의 spy라 브로커 없이 바로 성공을 돌려준다. 실패는 토픽·key(대회 ID)별로 실패한 결과를 돌려주게 바꿔 만든다.
 * 재시도 시각을 확인하는 테스트는 릴레이에 시각을 직접 넘긴다 (DB가 소수 6자리로 저장하므로 초 단위로 맞춘 시각을 쓴다).
 */
class ProductOutboxKafkaRelayTest extends ProductIntegrationTest {
	private static final long RACE_ID = 101;
	private static final long OTHER_RACE_ID = 102;

	@Autowired
	private ProductOutboxKafkaAppendUseCase productOutboxKafkaAppendUseCase;
	@Autowired
	private ProductOutboxKafkaRelayUseCase productOutboxKafkaRelayUseCase;

	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@Test
	@DisplayName("대회 저장 트랜잭션이 롤백되면 아웃박스 행도 남지 않는다")
	void rollbackWithBusinessTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID));
			status.setRollbackOnly();
		});

		assertThat(productOutboxKafkaEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("트랜잭션 밖에서 아웃박스에 저장하면 예외가 나고 행이 남지 않는다 (MANDATORY)")
	void rejectAppendOutsideTransaction() {
		assertThatThrownBy(() ->
			productOutboxKafkaAppendUseCase.appendRaceEvent(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID)))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(productOutboxKafkaEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("미발행 행을 남긴 순서대로 토픽·key(대회 ID)·본문 그대로 보내고, SENT와 보낸 시각을 남긴다")
	void relayPendingEventsInOrder() {
		append(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.ORDER_RACE_CANCEL, OTHER_RACE_ID);

		int sent = productFacade.relayKafkaOutbox();

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

		productFacade.relayKafkaOutbox();
		int sentAgain = productFacade.relayKafkaOutbox();

		assertThat(sentAgain).isZero();
		verify(kafka(), times(1)).send(eq(KafkaTopics.RACE_CHANGED), anyString(), anyString());
	}

	@Test
	@DisplayName("대회 101의 queuing.race.changed 메시지를 보내지 못하면 재시도 횟수를 1 올리고 1초 뒤에 다시 보내도록 두며, "
		+ "순서를 지키려고 같은 토픽·같은 대회의 뒤 메시지는 보내지 않지만 다른 대회 102의 같은 토픽 메시지는 보낸다")
	void blockOnlyFailedTopicAndRace() {
		failSendTo(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 1));
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 2));
		append(KafkaTopics.RACE_CHANGED, OTHER_RACE_ID, payload(OTHER_RACE_ID, 1));

		int sent = relayAt(now);

		assertThat(sent).isEqualTo(1);
		List<ProductOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(0).getRetryCount()).isEqualTo(1);
		assertThat(events.get(0).getNextRetryDate()).isEqualTo(now.plusSeconds(1));
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(1).getRetryCount()).isZero();
		assertThat(events.get(2).getStatus()).isEqualTo(OutboxStatus.SENT);
		verify(kafka(), never()).send(KafkaTopics.RACE_CHANGED, "101", jsonConverter.toJson(payload(RACE_ID, 2)));
	}

	@Test
	@DisplayName("대회 101의 race.filelink.requested 메시지가 막혀도, 토픽이 다른 같은 대회의 "
		+ "queuing.race.changed·queuing.racecourse.changed 메시지는 보낸다 (토픽이 다르면 순서를 지킬 필요가 없다)")
	void sendOtherTopicOfBlockedRace() {
		failSendTo(KafkaTopics.RACE_FILE_LINK_REQUESTED, RACE_ID);
		append(KafkaTopics.RACE_FILE_LINK_REQUESTED, RACE_ID);
		append(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.RACE_COURSE_CHANGED, RACE_ID);

		int sent = relayAt(now);

		assertThat(sent).isEqualTo(2);
		List<ProductOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(0).getRetryCount()).isEqualTo(1);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.SENT);
		assertThat(events.get(2).getStatus()).isEqualTo(OutboxStatus.SENT);
	}

	@Test
	@DisplayName("재시도 시각(실패 1초 뒤) 전에는 다시 보내지 않고 같은 토픽·같은 대회의 뒤 메시지도 기다리며, "
		+ "재시도 시각이 되면 막혔던 메시지부터 순서대로 보낸다")
	void waitUntilRetryDate() {
		failSendTo(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 1));
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 2));
		relayAt(now);
		succeedSendTo(KafkaTopics.RACE_CHANGED, RACE_ID);

		int sentBeforeRetryDate = relayAt(now.plusNanos(Duration.ofMillis(999).toNanos()));
		int sentAtRetryDate = relayAt(now.plusSeconds(1));

		assertThat(sentBeforeRetryDate).isZero();
		assertThat(sentAtRetryDate).isEqualTo(2);
		InOrder order = inOrder(kafka());
		order.verify(kafka(), times(2))
			.send(KafkaTopics.RACE_CHANGED, "101", jsonConverter.toJson(payload(RACE_ID, 1)));
		order.verify(kafka()).send(KafkaTopics.RACE_CHANGED, "101", jsonConverter.toJson(payload(RACE_ID, 2)));
		assertThat(allEvents()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT));
	}

	@Test
	@DisplayName("재시도 간격은 실패할 때마다 1초, 2초, 4초 …로 두 배씩 늘고 1분을 넘지 않는다")
	void doubleRetryDelayUpToOneMinute() {
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(1)).isEqualTo(Duration.ofSeconds(1));
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(2)).isEqualTo(Duration.ofSeconds(2));
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(3)).isEqualTo(Duration.ofSeconds(4));
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(6)).isEqualTo(Duration.ofSeconds(32));
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(7)).isEqualTo(Duration.ofMinutes(1));
		assertThat(ProductOutboxKafkaRelayUseCase.retryDelay(100)).isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	@DisplayName("MAX_RETRY번 보내지 못하면 SEND_FAILED로 바꾸고, 다음 릴레이부터는 그 행을 건너뛰고 같은 토픽·같은 대회의 뒤 메시지를 보낸다")
	void markFailedAfterMaxRetry() {
		failSendTo(KafkaTopics.RACE_CHANGED, RACE_ID);
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 1));
		append(KafkaTopics.RACE_CHANGED, RACE_ID, payload(RACE_ID, 2));

		// 재시도 간격(최대 1분)이 늘 지나도록 한 시간씩 뒤의 시각으로 릴레이를 돌린다
		for (int i = 0; i < ProductOutboxKafkaRelayUseCase.MAX_RETRY; i++) {
			relayAt(now.plusHours(i));
		}
		succeedSendTo(KafkaTopics.RACE_CHANGED, RACE_ID);
		int sent = relayAt(now.plusHours(ProductOutboxKafkaRelayUseCase.MAX_RETRY));

		assertThat(sent).isEqualTo(1);
		List<ProductOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.SEND_FAILED);
		assertThat(events.get(0).getRetryCount()).isEqualTo(ProductOutboxKafkaRelayUseCase.MAX_RETRY);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.SENT);
		verify(kafka(), times(ProductOutboxKafkaRelayUseCase.MAX_RETRY))
			.send(KafkaTopics.RACE_CHANGED, "101", jsonConverter.toJson(payload(RACE_ID, 1)));
	}

	private void append(String topic, long raceId) {
		append(topic, raceId, payload(raceId));
	}

	private void append(String topic, long raceId, Object payload) {
		transactionTemplate.executeWithoutResult(status ->
			productOutboxKafkaAppendUseCase.appendRaceEvent(topic, raceId, payload));
	}

	private static Map<String, Object> payload(long raceId) {
		return Map.of("raceId", raceId);
	}

	// 같은 대회의 메시지끼리 본문으로 구분한다 (seq: 남긴 순서)
	private static Map<String, Object> payload(long raceId, int seq) {
		return Map.of("raceId", raceId, "seq", seq);
	}

	// 트랜잭션 안에서 정한 시각으로 릴레이를 돌린다 (ProductFacade.relayKafkaOutbox는 지금 시각을 쓴다)
	private int relayAt(LocalDateTime time) {
		return transactionTemplate.execute(status -> productOutboxKafkaRelayUseCase.relay(time));
	}

	// 이 토픽·대회(key)의 메시지를 보내면 브로커가 거절한 것처럼 실패한 결과를 돌려준다
	private void failSendTo(String topic, long raceId) {
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 응답 없음")))
			.when(kafka()).send(eq(topic), eq(String.valueOf(raceId)), anyString());
	}

	// 이 토픽·대회(key)의 메시지를 다시 바로 성공으로 돌려준다
	private void succeedSendTo(String topic, long raceId) {
		doReturn(CompletableFuture.completedFuture(null))
			.when(kafka()).send(eq(topic), eq(String.valueOf(raceId)), anyString());
	}

	private List<ProductOutboxKafkaEvent> allEvents() {
		return productOutboxKafkaEventRepository.findAll().stream()
			.sorted((left, right) -> Long.compare(left.getId(), right.getId()))
			.toList();
	}
}
