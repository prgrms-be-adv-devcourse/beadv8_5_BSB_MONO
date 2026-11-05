package com.bukang.file.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.file.app.FileKafkaRetry;
import com.bukang.file.domain.FileDltKafkaEvent;
import com.bukang.file.out.FileDltKafkaEventRepository;
import com.bukang.file.support.FakeFileStorageConfig;

/**
 * 다시 보내기 API(POST /internal/v1/file/kafka-failures/{id}/resend)를 부른 뒤 DLT 실패 테이블의 행 상태를 확인한다.
 * 컨트롤러 → FileFacade → FileDltUseCase → DB(H2)까지 진짜로 돌리고, Kafka 전송만 가짜로 둔다.
 * file에는 공통 통합 테스트 클래스가 없어 다른 테스트처럼 설정을 직접 붙인다. Docker 없이 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class InternalV1FileDltControllerTest {
	private static final String TOPIC = KafkaTopics.RACE_FILE_LINK_REQUESTED;
	private static final String DLT_TOPIC = TOPIC + FileKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String FIRST_RETRY_TOPIC = "race.filelink.requested-retry-1000";
	private static final String MESSAGE_ID = "product-outbox-3b241101-e2bb-4255-8caf-4136c566aa43";
	private static final String MESSAGE_KEY = "301";
	private static final String PAYLOAD = "{\"refType\":\"RACE\",\"refId\":301,\"fileIds\":[11,12]}";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private FileDltKafkaEventRepository fileDltKafkaEventRepository;

	// 실제 KafkaTemplate을 감싼다. 자동 설정 빈의 타입이 KafkaTemplate<?, ?>라 같은 타입으로 받는다
	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	// 브로커 없이 보낸 것으로 처리한다 (보내지 않으면 send()가 브로커를 기다리다 max.block.ms 뒤 실패한다)
	@BeforeEach
	void setUpKafka() {
		doReturn(CompletableFuture.completedFuture(null)).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());
	}

	@AfterEach
	void cleanUpDlt() {
		fileDltKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("처리 실패 행을 다시 보내면 행이 다시 보냄 상태가 되고, 첫 재시도 토픽으로 키·본문·message-id째 1번 나간다")
	void resend() throws Exception {
		// given: DLT로 와서 처리 실패로 저장된 행
		long id = saveFailedRow();

		// when
		mockMvc.perform(post("/internal/v1/file/kafka-failures/{id}/resend", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("메시지를 다시 보냈습니다."))
			.andExpect(jsonPath("$.data.status").value("RESENT"));

		// then: DB의 행이 다시 보냄이다
		FileDltKafkaEvent row = fileDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.RESENT);
		assertThat(row.getResentDate()).isNotNull();

		// then: 첫 재시도 토픽으로 보냈다
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.captor();
		then(kafka()).should().send(captor.capture());
		ProducerRecord<String, String> sent = captor.getValue();
		assertThat(sent.topic()).isEqualTo(FIRST_RETRY_TOPIC);
		assertThat(sent.key()).isEqualTo(MESSAGE_KEY);
		assertThat(sent.value()).isEqualTo(PAYLOAD);
		assertThat(new String(sent.headers().lastHeader(KafkaMessageId.HEADER).value(), StandardCharsets.UTF_8))
			.isEqualTo(MESSAGE_ID);
	}

	@Test
	@DisplayName("Kafka로 보내지 못하면 503으로 답하고 행은 처리 실패로 되돌아간다")
	void resendFailed() throws Exception {
		// given: 처리 실패 행, 그리고 Kafka 전송이 실패한다
		long id = saveFailedRow();
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 연결 실패"))).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());

		// when
		mockMvc.perform(post("/internal/v1/file/kafka-failures/{id}/resend", id))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_RESEND_FAILED"));

		// then: 다시 보냄으로 바꿨던 행이 처리 실패로 돌아왔다
		FileDltKafkaEvent row = fileDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.FAILED);
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}

	// 파티션 0, 위치 7에서 처음 실패해 저장된 행의 ID
	private long saveFailedRow() {
		KafkaDltMessage message = new KafkaDltMessage(TOPIC, DLT_TOPIC, 0, 7L, MESSAGE_KEY, PAYLOAD, MESSAGE_ID,
			"java.lang.IllegalStateException", "파일 연결 실패");
		return fileDltKafkaEventRepository.save(new FileDltKafkaEvent(message)).getId();
	}
}
