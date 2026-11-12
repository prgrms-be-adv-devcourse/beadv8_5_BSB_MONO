package com.bukang.payout.in;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.payout.app.PayoutKafkaRetry;
import com.bukang.payout.domain.PayoutDltKafkaEvent;
import com.bukang.payout.out.PayoutDltKafkaEventRepository;

/**
 * 다시 보내기 API(POST /internal/v1/payout/kafka-failures/{id}/resend)를 부른 뒤 DLT 실패 테이블의 행 상태를 확인한다 (BUK-166)
 * 컨트롤러 → PayoutDltFacade → PayoutDltUseCase → DB(H2)까지 진짜로 돌리고, Kafka 전송만 가짜로 둔다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalV1PayoutDltControllerTest {
	private static final String TOPIC = KafkaTopics.ORDER_RACE_CANCEL;
	private static final String DLT_TOPIC = TOPIC + PayoutKafkaRetry.DLT_TOPIC_SUFFIX;
	// 정산 재시도 토픽의 첫 단계 (첫 지연 10초)
	private static final String FIRST_RETRY_TOPIC = "order.race.cancel.payout.retry-10000";
	private static final String MESSAGE_ID = "product-outbox-5d8a3f2e-1b4c-4e7a-9f0d-2c6b8e1a7d42";
	private static final String MESSAGE_KEY = "42";
	private static final String PAYLOAD = "{\"raceId\":42}";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private PayoutDltKafkaEventRepository payoutDltKafkaEventRepository;
	// 자동 설정 빈의 타입이 KafkaTemplate<?, ?>라 같은 타입으로 바꿔 끼운다 (다른 타입이면 빈이 둘이 돼 @RetryableTopic이 실패한다)
	@MockitoBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	@AfterEach
	void cleanUpDlt() {
		payoutDltKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("처리 실패 행을 다시 보내면 다시 보냄 상태가 되고, 정산의 첫 재시도 토픽으로 키·본문·message-id째 나간다")
	void resend() throws Exception {
		// given: DLT로 와서 처리 실패로 저장된 행, Kafka 전송은 성공한다
		long id = saveFailedRow();
		doReturn(CompletableFuture.completedFuture(null)).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());

		// when
		mockMvc.perform(post("/internal/v1/payout/kafka-failures/{id}/resend", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("메시지를 다시 보냈습니다."))
			.andExpect(jsonPath("$.data.status").value("RESENT"));

		// then: DB의 행이 다시 보냄이다
		PayoutDltKafkaEvent row = payoutDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.RESENT);
		assertThat(row.getResentDate()).isNotNull();

		// then: 주문 서비스는 받지 않는 정산의 첫 재시도 토픽으로 보냈다
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
		mockMvc.perform(post("/internal/v1/payout/kafka-failures/{id}/resend", id))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_RESEND_FAILED"));

		// then: 다시 보냄으로 바꿨던 행이 처리 실패로 돌아왔다
		PayoutDltKafkaEvent row = payoutDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.FAILED);
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}

	// 파티션 0, 위치 7에서 처음 실패해 저장된 행의 ID
	private long saveFailedRow() {
		KafkaDltMessage message = new KafkaDltMessage(TOPIC, DLT_TOPIC, 0, 7L, MESSAGE_KEY, PAYLOAD, MESSAGE_ID,
			"org.springframework.orm.ObjectOptimisticLockingFailureException", "정산 내역 잠금 충돌");
		return payoutDltKafkaEventRepository.save(new PayoutDltKafkaEvent(message)).getId();
	}
}
