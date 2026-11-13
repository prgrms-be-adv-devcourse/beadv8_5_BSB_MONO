package com.bukang.inquiry.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 알림이 만들어지는 때와 받는 사람, 보내는 메시지의 모양을 확인한다.
 * 알림은 문의·답변이 커밋된 뒤에 NotificationKafkaSender가 Kafka로 보낸다. 여기서는 Kafka를 목으로 바꿔 보낸 메시지를 본다
 */
class InquiryNotificationTest extends InquiryTestBase {
	@Autowired
	private InquiryUseCase inquiryUseCase;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private JsonConverter jsonConverter;

	@Test
	@DisplayName("판매자 담당 문의가 생기면 그 대회 판매자에게 INQUIRY_RECEIVED를 보낸다")
	void sellerInquiryNotifiesSeller() throws Exception {
		postAs(BASE + "/inquiries", ME, createJson(RACE, "RACE_VENUE", "주차는 어디에 하나요", "본문"))
			.andExpect(status().isCreated());

		List<NotificationRequestedEvent> events = sentEvents(1);
		NotificationRequestedEvent event = events.getFirst();
		long id = inquiryRepository.findAll().getFirst().getId();
		assertThat(event.memberId()).isEqualTo(SELLER);
		assertThat(event.kind()).isEqualTo(NotificationKind.INQUIRY_RECEIVED);
		assertThat(event.title()).isNotBlank();
		assertThat(event.body()).contains("주차는 어디에 하나요");
		assertThat(event.href()).isEqualTo("/seller/inquiries/" + id);
		assertThat(event.eventId()).isNotBlank();
	}

	@Test
	@DisplayName("관리자 담당 문의가 생겨도 알림은 보내지 않는다")
	void adminInquiryNoNotification() throws Exception {
		postAs(BASE + "/inquiries", ME, createJson(RACE, "APPLY_PAYMENT", "제목", "본문")).andExpect(status().isCreated());

		verify(kafkaTemplate, after(500).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("답변이 달리면 문의한 회원에게 INQUIRY_ANSWERED를 보낸다 (판매자 답변·관리자 답변 모두)")
	void answerNotifiesAsker() throws Exception {
		Inquiry bySeller = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry byAdmin = saveAdminInquiry(OTHER, RACE, SELLER);

		postAs(BASE + "/seller/inquiries/" + bySeller.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isOk());
		postAs(BASE + "/admin/inquiries/" + byAdmin.getId() + "/answer", ADMIN, answerJson("답"))
			.andExpect(status().isOk());

		List<NotificationRequestedEvent> events = sentEvents(2);
		assertThat(events).extracting(NotificationRequestedEvent::kind)
			.containsOnly(NotificationKind.INQUIRY_ANSWERED);
		assertThat(events).extracting(NotificationRequestedEvent::memberId).containsExactlyInAnyOrder(ME, OTHER);
		assertThat(events).extracting(NotificationRequestedEvent::href)
			.containsExactlyInAnyOrder("/me/inquiries/" + bySeller.getId(), "/me/inquiries/" + byAdmin.getId());
	}

	@Test
	@DisplayName("답변이 막히면(이미 답변·남의 문의) 알림을 보내지 않는다")
	void rejectedAnswerNoNotification() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isOk());
		sentEvents(1);

		postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", SELLER, answerJson("또"))
			.andExpect(status().isConflict());
		postAs(BASE + "/seller/inquiries/" + inquiry.getId() + "/answer", OTHER_SELLER, answerJson("남"))
			.andExpect(status().isNotFound());

		// 처음 한 번 보낸 것 말고는 늘지 않는다
		verify(kafkaTemplate, after(500).times(1)).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("문의 저장이 롤백되면 알림을 보내지 않는다 (커밋 뒤에만 보낸다)")
	void rollbackSendsNothing() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			inquiryUseCase.create(ME, SELLER, RACE, ORDER, InquiryType.RACE_VENUE, "제목", "본문");
			throw new IllegalStateException("롤백을 일으키는 실패");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(inquiryRepository.count()).isZero();
		verify(kafkaTemplate, after(500).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("Kafka가 알림을 보내다 실패해도 문의와 답변은 저장되고 응답은 성공이다")
	void kafkaFailureDoesNotBreakInquiry() throws Exception {
		doThrow(new IllegalStateException("브로커 없음")).when(kafkaTemplate).send(any(ProducerRecord.class));

		postAs(BASE + "/inquiries", ME, createJson(RACE, "RACE_VENUE", "제목", "본문")).andExpect(status().isCreated());
		Inquiry saved = inquiryRepository.findAll().getFirst();
		postAs(BASE + "/seller/inquiries/" + saved.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isOk());

		assertThat(inquiryRepository.count()).isEqualTo(1);
		assertThat(inquiryRepository.findById(saved.getId()).orElseThrow().getAnswer()).isEqualTo("답");
	}

	@Test
	@DisplayName("제목 100자·본문 1000자 문의도 알림 제목 100자·내용 500자·링크 300자를 넘지 않는다 (알림 서비스가 버리지 않게)")
	void notificationFitsReceiverLimits() throws Exception {
		postAs(BASE + "/inquiries", ME, createJson(RACE, "RACE_VENUE", "가".repeat(100), "나".repeat(1000)))
			.andExpect(status().isCreated());
		Inquiry saved = inquiryRepository.findAll().getFirst();
		postAs(BASE + "/seller/inquiries/" + saved.getId() + "/answer", SELLER, answerJson("다".repeat(2000)))
			.andExpect(status().isOk());

		for (NotificationRequestedEvent event : sentEvents(2)) {
			assertThat(event.title().length()).isLessThanOrEqualTo(100);
			assertThat(event.body().length()).isLessThanOrEqualTo(500);
			assertThat(event.href().length()).isLessThanOrEqualTo(300);
		}
	}

	@Test
	@DisplayName("보내는 Kafka 메시지는 알림 토픽, 키는 받는 회원 ID, 본문은 NotificationRequestedEvent JSON이다")
	void messageContract() throws Exception {
		postAs(BASE + "/inquiries", ME, createJson(RACE, "KIT_DELIVERY", "제목", "본문")).andExpect(status().isCreated());

		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.captor();
		verify(kafkaTemplate, timeout(3000)).send(captor.capture());
		ProducerRecord<String, String> record = captor.getValue();
		assertThat(record.topic()).isEqualTo(KafkaTopics.NOTIFICATION_REQUESTED);
		assertThat(record.key()).isEqualTo(String.valueOf(SELLER));
		assertThat(jsonConverter.fromJson(record.value(), NotificationRequestedEvent.class).kind())
			.isEqualTo(NotificationKind.INQUIRY_RECEIVED);
	}

	// 보낸 알림 메시지를 n개 기다렸다가 이벤트로 풀어 돌려준다
	private List<NotificationRequestedEvent> sentEvents(int count) {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.captor();
		verify(kafkaTemplate, timeout(3000).times(count)).send(captor.capture());
		return captor.getAllValues().stream()
			.map(record -> jsonConverter.fromJson(record.value(), NotificationRequestedEvent.class))
			.toList();
	}
}
