package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.order.app.OrderKafkaRetry;
import com.bukang.order.app.OrderOutboxKafkaAppendUseCase;
import com.bukang.order.domain.OrderDltKafkaEvent;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.out.OrderDltKafkaEventRepository;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

/**
 * 주문의 Kafka 실패 내부 API를 부른 뒤 DB 행을 확인한다 (BUK-180).
 * - DLT: /internal/v1/order/kafka-failures (목록, 다시 보내기, 처리 완료)
 * - 아웃박스: /internal/v1/order/outbox-failures (되돌리기의 404·409)
 * 컨트롤러 → Facade → UseCase → DB(H2)까지 진짜로 돌리고, Kafka 전송만 spy로 바꾼다.
 * spy 구성을 OrderOutboxKafkaRelayTest와 같게 두어 Spring 컨텍스트를 함께 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalV1OrderKafkaFailureControllerTest {
	private static final String DLT_URL = "/internal/v1/order/kafka-failures";
	private static final String OUTBOX_URL = "/internal/v1/order/outbox-failures";
	private static final String TOPIC = KafkaTopics.ORDER_RACE_CANCEL;
	private static final String DLT_TOPIC = TOPIC + OrderKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String FIRST_RETRY_TOPIC = "order.race.cancel.order.retry-10000";
	private static final String MESSAGE_ID = "product-outbox-5d8a3f2e-1b4c-4e7a-9f0d-2c6b8e1a7d42";
	private static final String MESSAGE_KEY = "300";
	private static final String PAYLOAD = "{\"raceId\":300,\"sellerId\":7,\"canceledAt\":\"2026-11-12T18:00:00\"}";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OrderDltKafkaEventRepository orderDltKafkaEventRepository;
	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;
	@Autowired
	private OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;
	@MockitoSpyBean
	private SlackNotifier slackNotifier;

	@BeforeEach
	void setUp() {
		doReturn(CompletableFuture.completedFuture(null)).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());
	}

	@AfterEach
	void tearDown() {
		orderDltKafkaEventRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("처리 실패 목록은 처리 실패 행만 돌려준다")
	void listFailed() throws Exception {
		long id = saveFailedRow();

		mockMvc.perform(get(DLT_URL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(id))
			.andExpect(jsonPath("$.data[0].originalTopic").value(TOPIC));
	}

	@Test
	@DisplayName("처리 실패 행을 다시 보내면 다시 보냄이 되고, 주문의 첫 재시도 토픽으로 키·본문·message-id째 나간다")
	void resend() throws Exception {
		long id = saveFailedRow();

		mockMvc.perform(post(DLT_URL + "/{id}/resend", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("메시지를 다시 보냈습니다."))
			.andExpect(jsonPath("$.data.status").value("RESENT"));

		OrderDltKafkaEvent row = orderDltKafkaEventRepository.findById(id).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(DltKafkaEventStatus.RESENT);
		assertThat(row.getResentDate()).isNotNull();

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
		long id = saveFailedRow();
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 연결 실패"))).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());

		mockMvc.perform(post(DLT_URL + "/{id}/resend", id))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_RESEND_FAILED"));

		assertThat(orderDltKafkaEventRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(DltKafkaEventStatus.FAILED);
	}

	@Test
	@DisplayName("메모와 함께 처리 완료로 닫으면 다시 닫을 수 없다 (409)")
	void complete() throws Exception {
		long id = saveFailedRow();
		String body = "{\"memo\":\"예치금 서비스에서 직접 환불해 닫음\"}";

		mockMvc.perform(post(DLT_URL + "/{id}/complete", id).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.completeMemo").value("예치금 서비스에서 직접 환불해 닫음"));
		mockMvc.perform(post(DLT_URL + "/{id}/complete", id).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_INVALID_STATUS"));
	}

	@Test
	@DisplayName("없는 DLT 행을 다시 보내면 404")
	void resendNotFound() throws Exception {
		mockMvc.perform(post(DLT_URL + "/{id}/resend", 999_999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("KAFKA_DLT_NOT_FOUND"));
	}

	@Test
	@DisplayName("발행 실패가 아닌 아웃박스 행을 되돌리면 409, 없는 행이면 404")
	void outboxRetryRejected() throws Exception {
		transactionTemplate.executeWithoutResult(status -> orderOutboxKafkaAppendUseCase.appendSeatRestore(1001));
		long id = orderOutboxKafkaEventRepository.findAll().stream().mapToLong(OrderOutboxKafkaEvent::getId)
			.max().orElseThrow();

		mockMvc.perform(post(OUTBOX_URL + "/{id}/retry", id))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_OUTBOX_INVALID_STATUS"));
		mockMvc.perform(post(OUTBOX_URL + "/{id}/retry", 999_999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_OUTBOX_NOT_FOUND"));
		mockMvc.perform(get(OUTBOX_URL).param("size", "101"))
			.andExpect(status().isBadRequest());
	}

	// 파티션 0, 위치 7에서 처음 실패해 저장된 행의 ID
	private long saveFailedRow() {
		KafkaDltMessage message = new KafkaDltMessage(TOPIC, DLT_TOPIC, 0, 7L, MESSAGE_KEY, PAYLOAD, MESSAGE_ID,
			"java.lang.IllegalStateException", "취소된 대회의 주문 1건을 환불하지 못해 다시 처리합니다. raceId=300");
		return orderDltKafkaEventRepository.save(new OrderDltKafkaEvent(message)).getId();
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}
}
