package com.bukang.file.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.BaseDltKafkaEvent;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.file.domain.FileDltKafkaEvent;
import com.bukang.file.out.FileDltKafkaEventRepository;

/**
 * DLT 실패 테이블의 행이 DLT 도착 → 다시 보냄 → 또 실패 → 처리 완료를 거치며 어떻게 바뀌는지 확인한다.
 * 저장소·Kafka·Slack은 가짜(@Mock)로 두고 FileDltUseCase만 진짜로 돌린다. Spring 컨텍스트와 Docker 없이 돈다.
 */
@ExtendWith(MockitoExtension.class)
class FileDltUseCaseTest {
	private static final String TOPIC = KafkaTopics.RACE_FILE_LINK_REQUESTED;
	private static final String DLT_TOPIC = TOPIC + FileKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String MESSAGE_ID = "product-outbox-43";
	private static final String MESSAGE_KEY = "301";
	private static final String PAYLOAD = "{\"refType\":\"RACE\",\"refId\":301,\"fileIds\":[11,12]}";
	private static final String EXCEPTION_CLASS = "java.lang.IllegalStateException";
	private static final String EXCEPTION_MESSAGE = "파일 연결 실패";
	private static final long ROW_ID = 15L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

	@Mock
	private FileDltKafkaEventRepository fileDltKafkaEventRepository;
	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;
	@Mock
	private SlackNotifier slackNotifier;
	@InjectMocks
	private FileDltUseCase fileDltUseCase;

	@Test
	@DisplayName("DLT로 처음 온 메시지는 처리 실패 행으로 저장되고, 토픽·DLT 위치·키·본문·message-id·예외가 그대로 담긴다")
	void recordFirstFailure() {
		// given
		given(fileDltKafkaEventRepository.findByMessageId(MESSAGE_ID)).willReturn(Optional.empty());
		given(fileDltKafkaEventRepository.save(any(FileDltKafkaEvent.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		fileDltUseCase.record(dltMessage(0, 7L));

		// then
		ArgumentCaptor<FileDltKafkaEvent> captor = ArgumentCaptor.forClass(FileDltKafkaEvent.class);
		then(fileDltKafkaEventRepository).should().save(captor.capture());
		FileDltKafkaEvent saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(DltKafkaEventStatus.FAILED);
		assertThat(saved.getOriginalTopic()).isEqualTo(TOPIC);
		assertThat(saved.getDltTopic()).isEqualTo(DLT_TOPIC);
		assertThat(saved.getDltPartition()).isZero();
		assertThat(saved.getDltOffset()).isEqualTo(7L);
		assertThat(saved.getMessageKey()).isEqualTo(MESSAGE_KEY);
		assertThat(saved.getPayload()).isEqualTo(PAYLOAD);
		assertThat(saved.getMessageId()).isEqualTo(MESSAGE_ID);
		assertThat(saved.getExceptionClass()).isEqualTo(EXCEPTION_CLASS);
		assertThat(saved.getExceptionMessage()).isEqualTo(EXCEPTION_MESSAGE);
		then(slackNotifier).should().sendAfterCommit(anyString());
	}

	@Test
	@DisplayName("처리 실패 행을 다시 보내면 다시 보냄 상태가 되고 다시 보낸 시각이 남는다")
	void markResent() {
		// given
		FileDltKafkaEvent failed = failedEvent();
		given(fileDltKafkaEventRepository.findById(ROW_ID)).willReturn(Optional.of(failed));

		// when
		FileDltKafkaEvent resent = fileDltUseCase.markResent(ROW_ID, NOW);

		// then
		assertThat(resent.getStatus()).isEqualTo(DltKafkaEventStatus.RESENT);
		assertThat(resent.getResentDate()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("다시 보낸 메시지가 또 실패하면 같은 행이 처리 실패로 돌아가 DLT 위치·예외가 마지막 값으로 바뀌고, 다시 보낼 수 있다")
	void failedAgainAfterResend() {
		// given: 파티션 0, 위치 7에서 처음 실패한 행을 다시 보냈다
		FileDltKafkaEvent event = failedEvent();
		given(fileDltKafkaEventRepository.findById(ROW_ID)).willReturn(Optional.of(event));
		given(fileDltKafkaEventRepository.findByMessageId(MESSAGE_ID)).willReturn(Optional.of(event));
		fileDltUseCase.markResent(ROW_ID, NOW);

		// when: 다시 보낸 메시지가 또 실패해 파티션 1, 위치 12로 들어왔다
		fileDltUseCase.record(dltMessage(1, 12L,
			"org.springframework.dao.DataIntegrityViolationException", "could not execute statement"));

		// then: 새 행을 만들지 않고 같은 행을 처리 실패로 되돌린다
		then(fileDltKafkaEventRepository).should(never()).save(any());
		assertThat(event.getStatus()).isEqualTo(DltKafkaEventStatus.FAILED);
		assertThat(event.getDltTopic()).isEqualTo(DLT_TOPIC);
		assertThat(event.getDltPartition()).isEqualTo(1);
		assertThat(event.getDltOffset()).isEqualTo(12L);
		assertThat(event.getExceptionClass()).isEqualTo("org.springframework.dao.DataIntegrityViolationException");
		assertThat(event.getExceptionMessage()).isEqualTo("could not execute statement");
		then(slackNotifier).should().sendAfterCommit(anyString());

		// when: 원인을 고친 뒤 다시 보낸다
		fileDltUseCase.markResent(ROW_ID, NOW.plusMinutes(10));

		// then
		assertThat(event.getStatus()).isEqualTo(DltKafkaEventStatus.RESENT);
		assertThat(event.getResentDate()).isEqualTo(NOW.plusMinutes(10));
	}

	@Test
	@DisplayName("다시 보낸 메시지를 본 리스너가 처리하면 행이 처리 완료가 되고 '다시 보내 처리됨' 메모가 남는다")
	void completeByResend() {
		// given: 다시 보냄 상태의 행
		FileDltKafkaEvent event = failedEvent();
		event.markResent(NOW);
		given(fileDltKafkaEventRepository.findByMessageId(MESSAGE_ID)).willReturn(Optional.of(event));

		// when
		fileDltUseCase.completeByResend(MESSAGE_ID, NOW.plusMinutes(1));

		// then
		assertThat(event.getStatus()).isEqualTo(DltKafkaEventStatus.COMPLETED);
		assertThat(event.getCompletedDate()).isEqualTo(NOW.plusMinutes(1));
		assertThat(event.getCompleteMemo()).isEqualTo(BaseDltKafkaEvent.AUTO_COMPLETE_MEMO);
	}

	// 파티션 0, 위치 7에서 처음 실패해 저장된 행
	private static FileDltKafkaEvent failedEvent() {
		return new FileDltKafkaEvent(dltMessage(0, 7L));
	}

	// DLT 토픽에 들어온 레코드를 DLT 리스너처럼 KafkaDltMessage로 바꾼다. 테스트마다 다른 값(위치, 예외)만 받는다
	private static KafkaDltMessage dltMessage(int partition, long offset) {
		return dltMessage(partition, offset, EXCEPTION_CLASS, EXCEPTION_MESSAGE);
	}

	private static KafkaDltMessage dltMessage(int partition, long offset, String exceptionClass,
		String exceptionMessage) {
		ConsumerRecord<String, String> record =
			new ConsumerRecord<>(DLT_TOPIC, partition, offset, MESSAGE_KEY, PAYLOAD);
		record.headers().add(KafkaMessageId.HEADER, bytes(MESSAGE_ID));
		record.headers().add(KafkaHeaders.EXCEPTION_CAUSE_FQCN, bytes(exceptionClass));
		record.headers().add(KafkaHeaders.EXCEPTION_MESSAGE, bytes(exceptionMessage));
		return KafkaDltMessage.from(record, FileKafkaRetry.DLT_TOPIC_SUFFIX);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
