package com.bukang.product.in;

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

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.product.app.ProductKafkaRetry;
import com.bukang.product.domain.ProductDltKafkaEvent;
import com.bukang.product.out.ProductDltKafkaEventRepository;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 다시 보내기 API(POST /internal/v1/product/kafka-failures/{id}/resend)를 부른 뒤 DLT 실패 테이블의 행 상태를 확인한다.
 * 컨트롤러 → ProductFacade → ProductDltUseCase → DB(H2)까지 진짜로 돌리고, Kafka 전송만 가짜로 둔다.
 * KafkaTemplate 가짜는 ProductIntegrationTest에 있어 다른 통합 테스트와 같은 Spring 컨텍스트를 쓴다 (ES 컨테이너 때문에 Docker 필요).
 */
class InternalV1ProductDltControllerTest extends ProductIntegrationTest {
	private static final String TOPIC = KafkaTopics.MEMBER_MEMBER_JOINED;
	private static final String DLT_TOPIC = TOPIC + ProductKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String FIRST_RETRY_TOPIC = "queuing.member.joined.product.retry-1000";
	private static final String MESSAGE_ID = "member-outbox-5d8a3f2e-1b4c-4e7a-9f0d-2c6b8e1a7d42";
	private static final String MESSAGE_KEY = "8001";
	private static final String PAYLOAD = "{\"id\":8001,\"nickname\":\"새러너\"}";

	@Autowired
	private ProductDltKafkaEventRepository productDltKafkaEventRepository;

	@AfterEach
	void cleanUpDlt() {
		productDltKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("처리 실패 행을 다시 보내면 행이 다시 보냄 상태가 되고, 첫 재시도 토픽으로 키·본문·message-id째 1번 나간다")
	void resend() throws Exception {
		// given: DLT로 와서 처리 실패로 저장된 행
		long id = saveFailedRow();

		// when
		mockMvc.perform(post("/internal/v1/product/kafka-failures/{id}/resend", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("메시지를 다시 보냈습니다."))
			.andExpect(jsonPath("$.data.status").value("RESENT"));

		// then: DB의 행이 다시 보냄이다
		ProductDltKafkaEvent row = productDltKafkaEventRepository.findById(id).orElseThrow();
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
		mockMvc.perform(post("/internal/v1/product/kafka-failures/{id}/resend", id))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_RESEND_FAILED"));

		// then: 다시 보냄으로 바꿨던 행이 처리 실패로 돌아왔다
		ProductDltKafkaEvent row = productDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.FAILED);
	}

	// 파티션 0, 위치 7에서 처음 실패해 저장된 행의 ID
	private long saveFailedRow() {
		KafkaDltMessage message = new KafkaDltMessage(TOPIC, DLT_TOPIC, 0, 7L, MESSAGE_KEY, PAYLOAD, MESSAGE_ID,
			"java.lang.IllegalStateException", "회원 복제본 저장 실패");
		return productDltKafkaEventRepository.save(new ProductDltKafkaEvent(message)).getId();
	}
}
