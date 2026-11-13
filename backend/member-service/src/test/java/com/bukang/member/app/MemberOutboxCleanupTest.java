package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.outbox.OutboxStatus;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.out.MemberOutboxEventRepository;

/**
 * 다 보낸 아웃박스 행 정리(MemberOutboxCleanupUseCase)를 확인한다 (BUK-41)
 * - 보낸 지 7일 지난 "보냄" 행만 지운다. 7일 안 된 "보냄", "보낼 차례", "포기" 행은 남긴다
 * - 한 번에 BATCH_SIZE건씩 나눠 지우고, 지울 행이 남지 않을 때까지 되풀이한다
 * 행의 보낸 시각은 발행 작업에 과거 시각을 넣어 만든다
 */
@SpringBootTest
@ActiveProfiles("test")
class MemberOutboxCleanupTest {
	@Autowired
	private MemberOutboxAppendUseCase memberOutboxAppendUseCase;
	@Autowired
	private MemberOutboxRelayUseCase memberOutboxRelayUseCase;
	@Autowired
	private MemberOutboxCleanupUseCase memberOutboxCleanupUseCase;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

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
	@DisplayName("보낸 지 7일 지난 보냄 행만 지우고, 7일이 안 된 보냄·포기·보낼 차례 행은 남긴다")
	void cleanupDeletesOnlySentOlderThanSevenDays() {
		append(1, 1);
		relayAt(now.minusDays(7).minusSeconds(1));
		append(2, 1);
		relayAt(now.minusDays(7).plusSeconds(1));
		failForever(3);
		append(4, 1);

		int deleted = memberOutboxCleanupUseCase.cleanup(now);

		assertThat(deleted).isEqualTo(1);
		assertThat(allRows()).extracting(MemberOutboxEvent::getRefId).containsExactly(2L, 3L, 4L);
		assertThat(allRows()).extracting(MemberOutboxEvent::getStatus).containsExactly(
			OutboxStatus.SENT, OutboxStatus.SEND_FAILED, OutboxStatus.PENDING);
	}

	@Test
	@DisplayName("지울 행이 한 번에 지우는 건수보다 많아도 나눠서 모두 지운다")
	void cleanupDeletesInChunksUntilDone() {
		int total = MemberOutboxCleanupUseCase.BATCH_SIZE + 1;
		append(1, total);
		LocalDateTime sentAt = now.minusDays(8);
		// 발행 작업은 한 번에 100건씩 보내므로 보낼 차례 행이 없을 때까지 돌린다
		int sent;
		do {
			sent = memberOutboxRelayUseCase.relay(sentAt);
		} while (sent > 0);

		int deleted = memberOutboxCleanupUseCase.cleanup(now);

		assertThat(deleted).isEqualTo(total);
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	// 회원 ID가 startId부터 하나씩 늘어나는 가입 행 count개를 한 트랜잭션에서 남긴다
	private void append(long startId, int count) {
		LocalDateTime time = LocalDateTime.now();
		transactionTemplate.executeWithoutResult(status -> {
			for (long id = startId; id < startId + count; id++) {
				memberOutboxAppendUseCase.appendMemberJoined(
					new MemberJoinedEvent(id, time, time, "회원" + id, false, UUID.randomUUID().toString()));
			}
		});
	}

	// 메시지 하나의 문제로 3번 실패시켜 포기 행을 만든다
	private void failForever(long memberId) {
		doReturn(CompletableFuture.failedFuture(new RecordTooLargeException("메시지가 너무 큽니다")))
			.when(kafka()).send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>argThat(
				record -> record != null && String.valueOf(memberId).equals(record.key())));
		append(memberId, 1);
		for (int i = 0; i < MemberOutboxRelayUseCase.MAX_MESSAGE_FAILURES; i++) {
			relayAt(now.plusHours(i));
		}
	}

	private void relayAt(LocalDateTime time) {
		memberOutboxRelayUseCase.relay(time);
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
}
