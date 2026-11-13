package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.HoldCancelEvent;
import com.bukang.common.shared.race.event.HoldRestoreEvent;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.OutboxStatus;
import com.bukang.order.domain.exception.OutboxEventNotFailedException;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

/**
 * 주문 아웃박스 저장(OrderOutboxKafkaAppendUseCase)과 릴레이(OrderOutboxKafkaRelayUseCase)를 확인한다.
 * - 저장은 취소·만료 트랜잭션 안에서만 할 수 있고, 그 트랜잭션이 롤백되면 행도 남지 않는다
 * - 릴레이는 미발행 행을 만든 순서대로 key(참가 신청 ID)를 붙여 보내고, 브로커가 받으면 SENT로 바꾼다
 * - 보내지 못하면 이번 릴레이를 멈추고, 재시도 시각까지는 그 토픽·신청만 막아 다른 토픽이나 다른 신청은 다음 릴레이에서 보낸다
 * - 재시도 간격은 1초부터 두 배씩 늘리고, MAX_RETRY번 실패하면 SEND_FAILED로 바꾼다
 * KafkaTemplate은 spy라 브로커 없이 바로 성공을 돌려준다. 실패는 토픽·key별로 실패한 결과를 돌려주게 바꿔 만든다.
 * 재시도 시각을 확인하는 테스트는 릴레이에 시각을 직접 넘긴다 (DB가 소수 6자리로 저장하므로 초 단위로 맞춘 시각을 쓴다).
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderOutboxKafkaRelayTest {
	private static final long PARTICIPATION_ID = 1001;
	private static final long OTHER_PARTICIPATION_ID = 1002;
	// 주문 아웃박스 message-id 형식: order-outbox-{UUID}
	private static final String OUTBOX_MESSAGE_ID_FORMAT =
		"order-outbox-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@Autowired
	private OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	@Autowired
	private OrderOutboxKafkaRelayUseCase orderOutboxKafkaRelayUseCase;
	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private JsonConverter jsonConverter;

	// 실제 KafkaTemplate을 감싼다. 자동 설정 빈의 타입이 KafkaTemplate<?, ?>라 같은 타입으로 받는다
	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;
	// 발행 실패 알림을 보냈는지 본다. 테스트는 webhook 주소가 비어 실제로는 로그만 남는다
	@MockitoSpyBean
	private SlackNotifier slackNotifier;

	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@BeforeEach
	void setUp() {
		doReturn(CompletableFuture.completedFuture(null)).when(kafka()).send(record(null, null, null));
	}

	@AfterEach
	void tearDown() {
		orderOutboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("취소 트랜잭션이 롤백되면 자리 복구 아웃박스 행도 남지 않는다")
	void rollbackWithBusinessTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			orderOutboxKafkaAppendUseCase.appendSeatRestore(PARTICIPATION_ID);
			status.setRollbackOnly();
		});

		assertThat(orderOutboxKafkaEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("트랜잭션 밖에서 아웃박스에 저장하면 예외가 나고 행이 남지 않는다 (MANDATORY)")
	void rejectAppendOutsideTransaction() {
		assertThatThrownBy(() -> orderOutboxKafkaAppendUseCase.appendSeatRestore(PARTICIPATION_ID))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(orderOutboxKafkaEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("신청 1001의 복구, 신청 1002의 해제를 남긴 순서대로 토픽·key(참가 신청 ID)·본문 그대로 보내고, SENT와 보낸 시각을 남긴다")
	void relayPendingEventsInOrder() {
		restore(PARTICIPATION_ID);
		release(OTHER_PARTICIPATION_ID);

		int sent = relayAt(now);

		assertThat(sent).isEqualTo(2);
		// 상품 서비스가 받는 본문 형식 ({participationId}) 그대로인지 글자로 확인한다
		assertThat(restorePayload(PARTICIPATION_ID)).isEqualTo("{\"participationId\":1001}");
		InOrder order = inOrder(kafka());
		order.verify(kafka()).send(record(KafkaTopics.PRODUCT_HOLD_RESTORE, "1001", restorePayload(PARTICIPATION_ID)));
		order.verify(kafka())
			.send(record(KafkaTopics.PRODUCT_HOLD_CANCEL, "1002", releasePayload(OTHER_PARTICIPATION_ID)));
		assertThat(allEvents()).allSatisfy(event -> {
			assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
			assertThat(event.getSentDate()).isEqualTo(now);
			assertThat(event.getRetryCount()).isZero();
			assertThat(event.getRefType()).isEqualTo("Participation");
		});
	}

	@Test
	@DisplayName("이미 보낸(SENT) 행은 다음 릴레이에서 다시 보내지 않는다")
	void skipSentEvents() {
		restore(PARTICIPATION_ID);

		relayAt(now);
		int sentAgain = relayAt(now.plusSeconds(1));

		assertThat(sentAgain).isZero();
		verify(kafka(), times(1)).send(record(KafkaTopics.PRODUCT_HOLD_RESTORE, null, null));
	}

	@Test
	@DisplayName("신청 1001의 복구 메시지를 보내지 못하면 재시도 횟수를 1 올려 1초 뒤에 다시 보내도록 두고, "
		+ "브로커 장애일 수 있어 이번 릴레이는 거기서 멈춰 뒤 메시지를 보내 보지도 않는다")
	void stopRelayOnFirstFailure() {
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		restore(OTHER_PARTICIPATION_ID);
		release(PARTICIPATION_ID);

		int sent = relayAt(now);

		assertThat(sent).isZero();
		List<OrderOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(0).getRetryCount()).isEqualTo(1);
		assertThat(events.get(0).getNextRetryDate()).isEqualTo(now.plusSeconds(1));
		assertThat(events.get(1).getRetryCount()).isZero();
		assertThat(events.get(2).getRetryCount()).isZero();
		verify(kafka(), times(1)).send(record(null, null, null));
	}

	@Test
	@DisplayName("재시도 시각 전의 다음 릴레이는 신청 1001의 복구 메시지와 같은 토픽·같은 신청의 뒤 메시지는 건너뛰고(순서 유지), "
		+ "다른 신청 1002의 복구와 같은 신청 1001의 해제(다른 토픽)는 보낸다")
	void skipOnlyBlockedTopicAndParticipationUntilRetryDate() {
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		restore(OTHER_PARTICIPATION_ID);
		release(PARTICIPATION_ID);
		relayAt(now);

		int sent = relayAt(now.plusNanos(Duration.ofMillis(500).toNanos()));

		assertThat(sent).isEqualTo(2);
		List<OrderOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(events.get(2).getStatus()).isEqualTo(OutboxStatus.SENT);
		assertThat(events.get(3).getStatus()).isEqualTo(OutboxStatus.SENT);
		verify(kafka(), times(1))
			.send(record(KafkaTopics.PRODUCT_HOLD_RESTORE, "1001", restorePayload(PARTICIPATION_ID)));
	}

	@Test
	@DisplayName("재시도 시각(실패 1초 뒤) 전에는 다시 보내지 않고, 재시도 시각이 되면 막혔던 메시지부터 순서대로 보낸다")
	void waitUntilRetryDate() {
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		relayAt(now);
		succeedSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);

		int sentBeforeRetryDate = relayAt(now.plusNanos(Duration.ofMillis(999).toNanos()));
		int sentAtRetryDate = relayAt(now.plusSeconds(1));

		assertThat(sentBeforeRetryDate).isZero();
		assertThat(sentAtRetryDate).isEqualTo(2);
		verify(kafka(), times(3))
			.send(record(KafkaTopics.PRODUCT_HOLD_RESTORE, "1001", restorePayload(PARTICIPATION_ID)));
		assertThat(allEvents()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT));
	}

	@Test
	@DisplayName("재시도 간격은 실패할 때마다 1초, 2초, 4초 …로 두 배씩 늘고 1분을 넘지 않는다")
	void doubleRetryDelayUpToOneMinute() {
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(1)).isEqualTo(Duration.ofSeconds(1));
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(2)).isEqualTo(Duration.ofSeconds(2));
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(3)).isEqualTo(Duration.ofSeconds(4));
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(6)).isEqualTo(Duration.ofSeconds(32));
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(7)).isEqualTo(Duration.ofMinutes(1));
		assertThat(OrderOutboxKafkaRelayUseCase.retryDelay(100)).isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	@DisplayName("MAX_RETRY번 보내지 못하면 SEND_FAILED로 바꾸고 더는 보내지 않으며, "
		+ "다음 릴레이부터는 같은 토픽·같은 신청의 뒤 메시지를 보낸다")
	void markFailedAfterMaxRetry() {
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		release(PARTICIPATION_ID);
		restore(PARTICIPATION_ID);

		// 재시도 간격(최대 1분)이 늘 지나도록 한 시간씩 뒤의 시각으로 릴레이를 돌린다
		for (int i = 0; i < OrderOutboxKafkaRelayUseCase.MAX_RETRY; i++) {
			relayAt(now.plusHours(i));
		}
		succeedSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		int sent = relayAt(now.plusHours(OrderOutboxKafkaRelayUseCase.MAX_RETRY));
		int sentAfterFailed = relayAt(now.plusHours(OrderOutboxKafkaRelayUseCase.MAX_RETRY + 1));

		// 실패할 때마다 릴레이가 멈춰 해제(둘째 행)도 기다렸다가, 첫 행이 SEND_FAILED가 된 뒤 둘째·셋째 행이 나간다
		assertThat(sent).isEqualTo(2);
		assertThat(sentAfterFailed).isZero();
		List<OrderOutboxKafkaEvent> events = allEvents();
		assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.SEND_FAILED);
		assertThat(events.get(0).getRetryCount()).isEqualTo(OrderOutboxKafkaRelayUseCase.MAX_RETRY);
		assertThat(events.get(1).getStatus()).isEqualTo(OutboxStatus.SENT);
		assertThat(events.get(2).getStatus()).isEqualTo(OutboxStatus.SENT);
		// 첫 행 MAX_RETRY번 + 셋째 행 1번
		verify(kafka(), times(OrderOutboxKafkaRelayUseCase.MAX_RETRY + 1))
			.send(record(KafkaTopics.PRODUCT_HOLD_RESTORE, "1001", restorePayload(PARTICIPATION_ID)));
	}

	@Test
	@DisplayName("발행 실패가 되면 Slack으로 한 번 알리고, 되돌리면 재시도 횟수·다음 시도 시각이 비워져 다음 릴레이가 다시 보낸다 (BUK-180)")
	void retrySendFailed() {
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		restore(PARTICIPATION_ID);
		for (int i = 0; i < OrderOutboxKafkaRelayUseCase.MAX_RETRY; i++) {
			relayAt(now.plusHours(i));
		}
		long id = allEvents().getFirst().getId();
		verify(slackNotifier, times(1)).sendAfterCommit(anyString());

		// 원인(브로커)을 고친 뒤 되돌린다
		succeedSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		orderOutboxKafkaRelayUseCase.retry(id, now);

		OrderOutboxKafkaEvent reset = orderOutboxKafkaEventRepository.findById(id).orElseThrow();
		assertThat(reset.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(reset.getRetryCount()).isZero();
		assertThat(reset.getNextRetryDate()).isNull();
		// 다음 시도 시각을 비웠으므로 마지막 실패보다 이른 시각에 돌려도 곧바로 보낸다
		assertThat(relayAt(now)).isEqualTo(1);
		assertThat(orderOutboxKafkaEventRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
	}

	@Test
	@DisplayName("발행 실패가 아닌 행(미처리·발행 완료)은 되돌리지 않는다")
	void rejectRetryUnlessSendFailed() {
		restore(PARTICIPATION_ID);
		long id = allEvents().getFirst().getId();

		assertThatThrownBy(() -> orderOutboxKafkaRelayUseCase.retry(id, now))
			.isInstanceOf(OutboxEventNotFailedException.class);
		relayAt(now);
		assertThatThrownBy(() -> orderOutboxKafkaRelayUseCase.retry(id, now))
			.isInstanceOf(OutboxEventNotFailedException.class)
			.hasMessage("아웃박스 메시지 " + id + "번은 발행 완료 상태라 다시 보낼 수 없습니다.");
	}

	@Test
	@DisplayName("보낼 때 행에 저장한 message-id(order-outbox-{UUID})를 헤더로 붙이고, 실패한 뒤 다시 보내도 같은 값이다")
	void messageIdHeader() {
		restore(PARTICIPATION_ID);
		failSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		relayAt(now);
		succeedSendTo(KafkaTopics.PRODUCT_HOLD_RESTORE, PARTICIPATION_ID);
		relayAt(now.plus(OrderOutboxKafkaRelayUseCase.FIRST_RETRY_DELAY));

		String messageId = allEvents().getFirst().getMessageId();
		assertThat(messageId).matches(OUTBOX_MESSAGE_ID_FORMAT);
		verify(kafka(), times(2)).send(withMessageId(messageId));
	}

	@Test
	@DisplayName("아웃박스 행마다 message-id가 다르다")
	void messageIdPerRow() {
		restore(PARTICIPATION_ID);
		release(PARTICIPATION_ID);

		assertThat(allEvents()).extracting(OrderOutboxKafkaEvent::getMessageId)
			.hasSize(2)
			.doesNotHaveDuplicates()
			.allMatch(messageId -> messageId.matches(OUTBOX_MESSAGE_ID_FORMAT));
	}

	private void restore(long participationId) {
		transactionTemplate.executeWithoutResult(status ->
			orderOutboxKafkaAppendUseCase.appendSeatRestore(participationId));
	}

	private void release(long participationId) {
		transactionTemplate.executeWithoutResult(status ->
			orderOutboxKafkaAppendUseCase.appendSeatRelease(participationId));
	}

	private String restorePayload(long participationId) {
		return jsonConverter.toJson(new HoldRestoreEvent(participationId));
	}

	private String releasePayload(long participationId) {
		return jsonConverter.toJson(new HoldCancelEvent(participationId));
	}

	private int relayAt(LocalDateTime time) {
		return orderOutboxKafkaRelayUseCase.relay(time);
	}

	// 이 토픽·신청(key)의 메시지를 보내면 브로커가 거절한 것처럼 실패한 결과를 돌려준다
	private void failSendTo(String topic, long participationId) {
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 응답 없음")))
			.when(kafka()).send(record(topic, String.valueOf(participationId), null));
	}

	private void succeedSendTo(String topic, long participationId) {
		doReturn(CompletableFuture.completedFuture(null))
			.when(kafka()).send(record(topic, String.valueOf(participationId), null));
	}

	// 보낸 레코드의 토픽·key·본문을 본다. null이면 그 값은 보지 않는다
	private static ProducerRecord<String, String> record(String topic, String key, String payload) {
		return argThat(record -> record != null
			&& (topic == null || topic.equals(record.topic()))
			&& (key == null || key.equals(record.key()))
			&& (payload == null || payload.equals(record.value())));
	}

	private static ProducerRecord<String, String> withMessageId(String messageId) {
		return argThat(record -> record != null && record.headers().lastHeader(KafkaMessageId.HEADER) != null
			&& messageId.equals(new String(record.headers().lastHeader(KafkaMessageId.HEADER).value(),
			StandardCharsets.UTF_8)));
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}

	private List<OrderOutboxKafkaEvent> allEvents() {
		return orderOutboxKafkaEventRepository.findAll().stream()
			.sorted(Comparator.comparing(OrderOutboxKafkaEvent::getId))
			.toList();
	}
}
