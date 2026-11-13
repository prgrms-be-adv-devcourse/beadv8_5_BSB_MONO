package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.board.domain.BoardOutboxKafkaEvent;
import com.bukang.board.domain.OutboxStatus;
import com.bukang.board.support.BoardTestBase;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

/**
 * 실패 경우 점검표 중 "외부 시스템 실패(Kafka 멈춤)": 브로커가 받았다고 해야 SENT로 바꾸고,
 * 못 보내면 PENDING으로 남겨 간격을 늘려 다시 보낸다. 다시 보내도 메시지 ID는 같다
 */
class BoardOutboxKafkaRelayTest extends BoardTestBase {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 12, 0, 0);

	@Autowired
	private BoardOutboxKafkaRelayUseCase relayUseCase;

	private BoardOutboxKafkaEvent pending(long postId) {
		return outboxRepository.save(BoardOutboxKafkaEvent.pending("BoardPost", postId,
			KafkaTopics.BOARD_POST_CHANGED, "{\"postId\":" + postId + "}"));
	}

	@SuppressWarnings("unchecked")
	private List<ProducerRecord<String, String>> sentRecords(int times) {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate, times(times)).send(captor.capture());
		return captor.getAllValues();
	}

	private void brokerDown() {
		when(kafkaTemplate.send(any(ProducerRecord.class)))
			.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
	}

	private void brokerUp() {
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
	}

	@Test
	@DisplayName("쌓인 순서대로 토픽·key·본문·message-id 헤더를 붙여 보내고 SENT로 바꾼다. 다시 돌려도 또 보내지 않는다")
	void sendsInOrder() {
		BoardOutboxKafkaEvent first = pending(7);
		BoardOutboxKafkaEvent second = pending(8);

		assertThat(relayUseCase.relay(NOW)).isEqualTo(2);

		List<ProducerRecord<String, String>> sent = sentRecords(2);
		assertThat(sent.get(0).topic()).isEqualTo(KafkaTopics.BOARD_POST_CHANGED);
		assertThat(sent.get(0).key()).isEqualTo("7");
		assertThat(sent.get(0).value()).isEqualTo(first.getPayload());
		assertThat(sent.get(1).key()).isEqualTo("8");
		assertThat(new String(sent.get(0).headers().lastHeader(KafkaMessageId.HEADER).value(),
			StandardCharsets.UTF_8)).isEqualTo(first.getMessageId());
		assertThat(outboxRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
		assertThat(outboxRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);

		clearInvocations(kafkaTemplate);
		assertThat(relayUseCase.relay(NOW.plusSeconds(5))).isZero();
		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("브로커가 멈추면 PENDING으로 남기고 실패 횟수를 올리며, 뒤 메시지도 이번에는 보내지 않는다")
	void brokerDownKeepsPending() {
		BoardOutboxKafkaEvent first = pending(7);
		BoardOutboxKafkaEvent second = pending(8);
		brokerDown();

		assertThat(relayUseCase.relay(NOW)).isZero();

		BoardOutboxKafkaEvent failed = outboxRepository.findById(first.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(failed.getRetryCount()).isEqualTo(1);
		assertThat(failed.getNextRetryDate()).isAfter(NOW);
		assertThat(outboxRepository.findById(second.getId()).orElseThrow().getRetryCount()).isZero();
		verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("재시도 시각 전에는 다시 보내지 않고, 지나면 같은 메시지 ID로 다시 보내 SENT가 된다 (밀린 글이 살아나면 나간다)")
	void retriesWithSameMessageId() {
		BoardOutboxKafkaEvent event = pending(7);
		brokerDown();
		relayUseCase.relay(NOW);
		LocalDateTime retryAt = outboxRepository.findById(event.getId()).orElseThrow().getNextRetryDate();
		clearInvocations(kafkaTemplate);
		brokerUp();

		assertThat(relayUseCase.relay(retryAt.minusNanos(1000))).isZero();
		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

		assertThat(relayUseCase.relay(retryAt)).isEqualTo(1);
		ProducerRecord<String, String> resent = sentRecords(1).getFirst();
		assertThat(new String(resent.headers().lastHeader(KafkaMessageId.HEADER).value(), StandardCharsets.UTF_8))
			.isEqualTo(event.getMessageId());
		assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
	}

	@Test
	@DisplayName("재시도를 기다리는 글의 뒤 메시지는 막히고, 다른 글의 메시지는 그대로 나간다")
	void blocksOnlySameKey() {
		BoardOutboxKafkaEvent waiting = pending(7);
		waiting.markSendFailed(BoardOutboxKafkaRelayUseCase.MAX_RETRY, NOW.plusMinutes(1));
		outboxRepository.save(waiting);
		BoardOutboxKafkaEvent sameKeyLater = pending(7);
		BoardOutboxKafkaEvent otherKey = pending(8);

		assertThat(relayUseCase.relay(NOW)).isEqualTo(1);

		assertThat(outboxRepository.findById(sameKeyLater.getId()).orElseThrow().getStatus())
			.isEqualTo(OutboxStatus.PENDING);
		assertThat(outboxRepository.findById(otherKey.getId()).orElseThrow().getStatus())
			.isEqualTo(OutboxStatus.SENT);
	}

	@Test
	@DisplayName("MAX_RETRY번 실패하면 SEND_FAILED로 바꿔 더는 보내지 않는다")
	void givesUpAfterMaxRetry() {
		BoardOutboxKafkaEvent event = pending(7);
		brokerDown();
		LocalDateTime now = NOW;
		for (int i = 0; i < BoardOutboxKafkaRelayUseCase.MAX_RETRY; i++) {
			relayUseCase.relay(now);
			now = now.plusMinutes(2);
		}

		BoardOutboxKafkaEvent failed = outboxRepository.findById(event.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.SEND_FAILED);
		clearInvocations(kafkaTemplate);
		brokerUp();
		assertThat(relayUseCase.relay(now.plusHours(1))).isZero();
		verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
	}
}
