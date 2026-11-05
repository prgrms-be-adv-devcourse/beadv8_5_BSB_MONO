package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.MemberOutboxStatus;
import com.bukang.member.out.MemberOutboxEventRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * 아웃박스 발행 작업(MemberOutboxRelayUseCase)을 확인한다 (BUK-41)
 * - 보낼 차례 행을 id 순서로, key=대상 ID로 보내고 브로커가 받으면 "보냄"과 보낸 시각을 남긴다
 * - Kafka 연결 문제는 포기하지 않고 1초부터 두 배씩 최대 1분 간격으로 다시 보낸다
 * - 메시지 하나의 문제는 3번째 실패에 "포기"로 바꾸고, 같은 대상의 뒤 행은 다음 실행에 나간다
 * - 보내지 못하면 이번 실행을 멈추고, 재시도 시각까지는 같은 대상(종류 + ID)의 뒤 행만 막는다
 * - 다른 트랜잭션(다른 서버)이 락을 건 행은 기다리지 않고 건너뛴다 (SKIP LOCKED)
 * KafkaTemplate은 spy라 브로커 없이 바로 성공을 돌려준다. 실패는 대상 ID별로 실패한 결과를 돌려주게 바꿔 만든다.
 * 행은 회원 데이터 없이 아웃박스 저장 메서드를 트랜잭션 안에서 직접 불러 만든다
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class MemberOutboxRelayTest {
	private static final long MEMBER_ID = 1001;
	private static final long OTHER_MEMBER_ID = 1002;

	@Autowired
	private MemberOutboxAppendUseCase memberOutboxAppendUseCase;
	@Autowired
	private MemberOutboxRelayUseCase memberOutboxRelayUseCase;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private EntityManager entityManager;

	// 실제 KafkaTemplate을 감싼다. 자동 설정 빈의 타입이 KafkaTemplate<?, ?>라 같은 타입으로 받는다
	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@BeforeEach
	void setUp() {
		memberOutboxEventRepository.deleteAll();
		doReturn(CompletableFuture.completedFuture(null)).when(kafka()).send(any(ProducerRecord.class));
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
	}

	@Test
	@DisplayName("보낼 차례 행을 저장 순서대로 토픽·key(대상 ID)·본문 그대로 보내고, 보냄과 보낸 시각을 남긴다")
	void relayPendingRowsInOrder() {
		joined(MEMBER_ID);
		seller(OTHER_MEMBER_ID);

		int sent = relayAt(now);

		assertThat(sent).isEqualTo(2);
		List<ProducerRecord<String, String>> records = sentRecords(2);
		List<MemberOutboxEvent> rows = allRows();
		assertThat(records.get(0).topic()).isEqualTo(KafkaTopics.MEMBER_MEMBER_JOINED);
		assertThat(records.get(0).key()).isEqualTo("1001");
		assertThat(records.get(0).value()).isEqualTo(rows.get(0).getPayload());
		assertThat(records.get(1).topic()).isEqualTo(KafkaTopics.SELLER_CHANGED);
		assertThat(records.get(1).key()).isEqualTo("1002");
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.SENT);
			assertThat(row.getSentDate()).isEqualTo(now);
			assertThat(row.getRetryCount()).isZero();
		});
	}

	@Test
	@DisplayName("message-id 헤더에 행에 저장한 값을 그대로 보내고, 보내지 못해 다시 보낼 때도 같은 값이 나간다")
	void resendKeepsStoredMessageId() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		relayAt(now);
		succeedSendTo(MEMBER_ID);

		int sent = relayAt(now.plusSeconds(1));

		assertThat(sent).isEqualTo(1);
		String stored = allRows().get(0).getMessageId();
		assertThat(sentRecords(2)).extracting(MemberOutboxRelayTest::messageIdHeader)
			.containsExactly(stored, stored);
	}

	@Test
	@DisplayName("message_id 컬럼을 더하기 전에 만든 행(값 없음)은 보낼 때 member-outbox-{UUID}를 한 번 채워 저장하고, "
		+ "보내지 못해 다시 보낼 때도 그 값이 나간다")
	void legacyRowGetsMessageIdOnceOnRelay() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		transactionTemplate.executeWithoutResult(status ->
			entityManager.createNativeQuery("update member_outbox_event set message_id = null").executeUpdate());
		relayAt(now);
		String assigned = allRows().get(0).getMessageId();
		succeedSendTo(MEMBER_ID);

		relayAt(now.plusSeconds(1));

		assertThat(assigned).matches("member-outbox-[0-9a-f-]{36}");
		assertThat(sentRecords(2)).extracting(MemberOutboxRelayTest::messageIdHeader)
			.containsExactly(assigned, assigned);
	}

	@Test
	@DisplayName("이미 보낸 행은 다음 실행에서 다시 보내지 않는다")
	void skipSentRows() {
		joined(MEMBER_ID);

		relayAt(now);
		int sentAgain = relayAt(now.plusSeconds(1));

		assertThat(sentAgain).isZero();
		verify(kafka(), times(1)).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("Kafka 연결 문제로 보내지 못하면 실패 횟수를 1 올려 1초 뒤에 다시 보내도록 하고, "
		+ "이번 실행은 거기서 멈춰 다른 회원의 행도 보내 보지 않는다")
	void connectionFailureStopsRelayAndRetriesAfterOneSecond() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		joined(OTHER_MEMBER_ID);

		int sent = relayAt(now);

		assertThat(sent).isZero();
		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows.get(0).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(rows.get(0).getRetryCount()).isEqualTo(1);
		assertThat(rows.get(0).getNextRetryDate()).isEqualTo(now.plusSeconds(1));
		assertThat(rows.get(1).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(rows.get(1).getRetryCount()).isZero();
		verify(kafka(), times(1)).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("Kafka 연결 문제는 열 번 실패해도 포기하지 않고, 다시 보낼 간격은 1분을 넘지 않는다")
	void connectionFailureNeverGivesUp() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);

		// 재시도 간격(최대 1분)이 늘 지나도록 한 시간씩 뒤의 시각으로 돌린다
		LocalDateTime lastRelay = now;
		for (int i = 0; i < 10; i++) {
			lastRelay = now.plusHours(i);
			relayAt(lastRelay);
		}

		MemberOutboxEvent row = allRows().get(0);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRetryCount()).isEqualTo(10);
		assertThat(row.getNextRetryDate()).isEqualTo(lastRelay.plusMinutes(1));
	}

	@Test
	@DisplayName("다시 보낼 간격은 실패할 때마다 1초, 2초, 4초 …로 두 배씩 늘고 1분을 넘지 않는다")
	void doubleRetryDelayUpToOneMinute() {
		assertThat(MemberOutboxRelayUseCase.retryDelay(1)).isEqualTo(Duration.ofSeconds(1));
		assertThat(MemberOutboxRelayUseCase.retryDelay(2)).isEqualTo(Duration.ofSeconds(2));
		assertThat(MemberOutboxRelayUseCase.retryDelay(3)).isEqualTo(Duration.ofSeconds(4));
		assertThat(MemberOutboxRelayUseCase.retryDelay(6)).isEqualTo(Duration.ofSeconds(32));
		assertThat(MemberOutboxRelayUseCase.retryDelay(7)).isEqualTo(Duration.ofMinutes(1));
		assertThat(MemberOutboxRelayUseCase.retryDelay(1000)).isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	@DisplayName("메시지 하나의 문제(크기 초과)는 3번째 실패에 포기로 바꾸고 에러 로그를 남기며, "
		+ "다음 실행부터 같은 회원의 뒤 행을 보낸다")
	void messageFailureGivesUpOnThirdTry(CapturedOutput output) {
		failSendTo(MEMBER_ID, new RecordTooLargeException("메시지가 너무 큽니다"));
		joined(MEMBER_ID);
		modified(MEMBER_ID);

		relayAt(now);
		relayAt(now.plusHours(1));
		MemberOutboxEvent afterTwoFailures = allRows().get(0);
		relayAt(now.plusHours(2));
		succeedSendTo(MEMBER_ID);
		int sent = relayAt(now.plusHours(3));

		assertThat(afterTwoFailures.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows.get(0).getStatus()).isEqualTo(MemberOutboxStatus.SEND_FAILED);
		assertThat(rows.get(0).getRetryCount()).isEqualTo(MemberOutboxRelayUseCase.MAX_MESSAGE_FAILURES);
		assertThat(rows.get(1).getStatus()).isEqualTo(MemberOutboxStatus.SENT);
		assertThat(sent).isEqualTo(1);
		assertThat(output).contains("포기", rows.get(0).getEventId());
	}

	@Test
	@DisplayName("재시도 시각 전에는 같은 대상(회원 1001)의 뒤 행만 건너뛰고, 다른 회원과 ID 숫자가 같은 판매자 1001의 행은 보낸다")
	void blockOnlySameTargetUntilRetryDate() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		modified(MEMBER_ID);
		joined(OTHER_MEMBER_ID);
		seller(MEMBER_ID);
		relayAt(now);
		succeedSendTo(MEMBER_ID);

		int sent = relayAt(now.plusNanos(Duration.ofMillis(500).toNanos()));

		assertThat(sent).isEqualTo(2);
		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows.get(0).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(rows.get(1).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(rows.get(2).getStatus()).isEqualTo(MemberOutboxStatus.SENT);
		assertThat(rows.get(3).getStatus()).isEqualTo(MemberOutboxStatus.SENT);
	}

	@Test
	@DisplayName("재시도 시각이 되면 막혔던 행부터 저장 순서대로 보낸다")
	void sendBlockedRowsInOrderAtRetryDate() {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		modified(MEMBER_ID);
		relayAt(now);
		succeedSendTo(MEMBER_ID);

		int sent = relayAt(now.plusSeconds(1));

		assertThat(sent).isEqualTo(2);
		List<ProducerRecord<String, String>> records = sentRecords(3);
		assertThat(records.get(1).topic()).isEqualTo(KafkaTopics.MEMBER_MEMBER_JOINED);
		assertThat(records.get(2).topic()).isEqualTo(KafkaTopics.MEMBER_MODIFIED);
	}

	@Test
	@DisplayName("다른 서버가 락을 건 행은 기다리지 않고 건너뛰어, 같은 행을 두 서버가 함께 보내지 않는다 (SKIP LOCKED)")
	void skipRowsLockedByAnotherRelay() throws Exception {
		joined(MEMBER_ID);
		joined(OTHER_MEMBER_ID);

		int sent = whileAnotherRelayLocksFirstRow(() -> relayAt(now));

		assertThat(sent).isEqualTo(1);
		List<ProducerRecord<String, String>> records = sentRecords(1);
		assertThat(records.get(0).key()).isEqualTo("1002");
		assertThat(allRows().get(0).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
	}

	@Test
	@DisplayName("보낼 차례 행이 5분 넘게 남아 있으면 에러 로그를 남긴다")
	void logErrorWhenPendingOlderThanFiveMinutes(CapturedOutput output) {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);

		relayAt(LocalDateTime.now().plusMinutes(6));

		assertThat(output).contains("5분 넘게");
	}

	@Test
	@DisplayName("5분 넘은 보낼 차례 행 에러 로그는 매 실행이 아니라 1분에 한 번만 남긴다")
	void logStalePendingAtMostOncePerMinute(CapturedOutput output) {
		failSendTo(MEMBER_ID, new TimeoutException("브로커 응답 없음"));
		joined(MEMBER_ID);
		// 마지막으로 남긴 시각은 공용 빈에 남으므로, 다른 테스트의 실행 시각과 겹치지 않게 하루 뒤로 돌린다
		LocalDateTime later = LocalDateTime.now().plusDays(1);

		relayAt(later);
		relayAt(later.plusSeconds(1));
		relayAt(later.plusSeconds(2));

		assertThat(output.getAll().split("5분 넘게", -1)).hasSize(2);
	}

	@Test
	@DisplayName("다른 서버가 회원 1001의 앞 행에 락을 쥐고 있으면, 회원 1001의 뒤 행은 먼저 보내지 않고 다른 회원 행만 보낸다")
	void keepOrderWhenAnotherRelayHoldsEarlierRow() throws Exception {
		joined(MEMBER_ID);
		modified(MEMBER_ID);
		joined(OTHER_MEMBER_ID);

		int sent = whileAnotherRelayLocksFirstRow(() -> relayAt(now));

		assertThat(sent).isEqualTo(1);
		assertThat(sentRecords(1).get(0).key()).isEqualTo("1002");
		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows.get(1).getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
	}

	@Test
	@DisplayName("메시지 문제로 정하지 않은 오류(send가 바로 던진 예외 포함)는 연결 문제로 보고 다섯 번 실패해도 포기하지 않는다")
	void unknownFailureIsTreatedAsConnectionProblem() {
		doThrow(new IllegalStateException("알 수 없는 오류")).when(kafka()).send(keyIs(MEMBER_ID));
		joined(MEMBER_ID);

		for (int i = 0; i < 5; i++) {
			relayAt(now.plusHours(i));
		}

		MemberOutboxEvent row = allRows().get(0);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRetryCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("발행 작업은 READ COMMITTED로 돈다. MySQL 기본(REPEATABLE READ)이면 FOR UPDATE가 인덱스 끝 빈 구간까지 락을 걸어, "
		+ "보내는 동안 가입·정보 변경의 아웃박스 저장이 Kafka 응답을 함께 기다린다")
	void relayRunsInReadCommitted() {
		Integer[] isolation = new Integer[1];
		doAnswer(invocation -> {
			isolation[0] = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
			return CompletableFuture.completedFuture(null);
		}).when(kafka()).send(any(ProducerRecord.class));
		joined(MEMBER_ID);

		relayAt(now);

		assertThat(isolation[0]).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
	}

	@Test
	@DisplayName("send가 그 자리에서 KafkaException(원인 크기 초과)을 던져도 메시지 문제로 보고 3번째 실패에 포기한다")
	void syncRecordTooLargeGivesUpOnThirdTry() {
		doThrow(new KafkaException("Send failed", new RecordTooLargeException("메시지가 너무 큽니다")))
			.when(kafka()).send(keyIs(MEMBER_ID));

		assertGivesUpOnThirdTry();
	}

	@Test
	@DisplayName("직렬화 오류는 메시지 문제로 보고 3번째 실패에 포기한다")
	void serializationFailureGivesUpOnThirdTry() {
		doThrow(new SerializationException("직렬화 실패")).when(kafka()).send(keyIs(MEMBER_ID));

		assertGivesUpOnThirdTry();
	}

	@Test
	@DisplayName("메타데이터 대기 시간 초과의 원인이 잘못된 토픽이면 메시지 문제로 보고 3번째 실패에 포기한다")
	void invalidTopicInsideTimeoutGivesUpOnThirdTry() {
		failSendTo(MEMBER_ID, new TimeoutException("메타데이터 대기 시간 초과", new InvalidTopicException("잘못된 토픽")));

		assertGivesUpOnThirdTry();
	}

	private void assertGivesUpOnThirdTry() {
		joined(MEMBER_ID);

		for (int i = 0; i < MemberOutboxRelayUseCase.MAX_MESSAGE_FAILURES; i++) {
			relayAt(now.plusHours(i));
		}

		MemberOutboxEvent row = allRows().get(0);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.SEND_FAILED);
		assertThat(row.getRetryCount()).isEqualTo(MemberOutboxRelayUseCase.MAX_MESSAGE_FAILURES);
	}

	// 다른 서버의 발행 작업이 첫 행에 락을 쥔 채 머무는 동안 relay를 돌린다
	// H2는 FETCH FIRST와 함께 쓴 FOR UPDATE가 조건에 맞는 행을 모두 잠가, 첫 행 하나만 ID로 잠근다
	private int whileAnotherRelayLocksFirstRow(IntSupplier relay) throws Exception {
		long firstId = allRows().get(0).getId();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService anotherServer = Executors.newSingleThreadExecutor();
		try {
			Future<?> holder = anotherServer.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				entityManager.find(MemberOutboxEvent.class, firstId, LockModeType.PESSIMISTIC_WRITE);
				locked.countDown();
				await(release);
			}));
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
			int sent = relay.getAsInt();
			release.countDown();
			holder.get(5, TimeUnit.SECONDS);
			return sent;
		} finally {
			release.countDown();
			anotherServer.shutdownNow();
		}
	}

	private void joined(long memberId) {
		LocalDateTime time = LocalDateTime.now();
		transactionTemplate.executeWithoutResult(status -> memberOutboxAppendUseCase.appendMemberJoined(
			new MemberJoinedEvent(memberId, time, time, "회원" + memberId, false, UUID.randomUUID().toString())));
	}

	private void modified(long memberId) {
		LocalDateTime time = LocalDateTime.now();
		transactionTemplate.executeWithoutResult(status -> memberOutboxAppendUseCase.appendMemberModified(
			new MemberModifiedEvent(memberId, time, time, "수정" + memberId, UUID.randomUUID().toString())));
	}

	private void seller(long sellerId) {
		transactionTemplate.executeWithoutResult(status -> memberOutboxAppendUseCase.appendSellerChanged(
			new SellerChangedEvent(sellerId, 1L, SellerType.INDIVIDUAL, null, null, "대표" + sellerId,
				SellerStatus.PENDING, LocalDateTime.now(), null, UUID.randomUUID().toString())));
	}

	private int relayAt(LocalDateTime time) {
		return memberOutboxRelayUseCase.relay(time);
	}

	// 이 대상 ID(key)의 메시지를 보내면 실패한 결과를 돌려준다
	private void failSendTo(long refId, Exception cause) {
		doReturn(CompletableFuture.failedFuture(cause)).when(kafka()).send(keyIs(refId));
	}

	private void succeedSendTo(long refId) {
		doReturn(CompletableFuture.completedFuture(null)).when(kafka()).send(keyIs(refId));
	}

	private static String messageIdHeader(ProducerRecord<String, String> record) {
		Header header = record.headers().lastHeader(KafkaMessageId.HEADER);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}

	private static ProducerRecord<String, String> keyIs(long refId) {
		return argThat(record -> record != null && String.valueOf(refId).equals(record.key()));
	}

	@SuppressWarnings("unchecked")
	private List<ProducerRecord<String, String>> sentRecords(int count) {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafka(), times(count)).send(captor.capture());
		return captor.getAllValues();
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}

	private List<MemberOutboxEvent> allRows() {
		return memberOutboxEventRepository.findAll().stream()
			.sorted(Comparator.comparing(MemberOutboxEvent::getId))
			.toList();
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
