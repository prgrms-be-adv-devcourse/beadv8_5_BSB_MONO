package com.bukang.inquiry.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 설계 9장: 회원 문의는 주문 하나와 대회 하나에 묶고, 판매자가 관리자에게 보내는 문의는 주문·대회 없이 보낸다
 */
class InquiryOrderLinkTest extends InquiryTestBase {
	private static final String CREATE_URL = BASE + "/inquiries";

	@Test
	@DisplayName("내 주문에 있는 대회로 문의하면 주문 ID와 대회 ID가 함께 저장된다")
	void createWithOrder() throws Exception {
		postAs(CREATE_URL, ME, createJson(RACE, "RACE_VENUE", "제목", "본문"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.orderId").value(ORDER))
			.andExpect(jsonPath("$.data.raceId").value(RACE));

		Inquiry saved = inquiryRepository.findAll().getFirst();
		assertThat(saved.getOrderId()).isEqualTo(ORDER);
		assertThat(saved.getRaceId()).isEqualTo(RACE);
		assertThat(saved.getSellerMemberId()).isEqualTo(SELLER);
	}

	@Test
	@DisplayName("남의 주문으로 문의하면 404이고 저장하지 않는다 (남의 주문이 있는지 알려 주지 않는다)")
	void othersOrder() throws Exception {
		postAs(CREATE_URL, ME, createJsonOf(RACE, OTHER_ORDER, "ETC", "제목", "본문"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_ORDER_NOT_FOUND"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("주문에 없는 대회로 문의하면 404이고 저장하지 않는다")
	void raceNotInOrder() throws Exception {
		raceSellerPort.race(777L, SELLER);

		postAs(CREATE_URL, ME, createJsonOf(777L, ORDER, "ETC", "제목", "본문"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_ORDER_NOT_FOUND"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("없는 주문으로 문의하면 404이고 저장하지 않는다")
	void noOrder() throws Exception {
		postAs(CREATE_URL, ME, createJsonOf(RACE, NO_ORDER, "ETC", "제목", "본문"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_ORDER_NOT_FOUND"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("주문 서비스가 멈추면 503이고 저장하지 않으며 알림도 보내지 않는다")
	void orderServiceUnavailable() throws Exception {
		orderLookupPort.setUnavailable(true);

		postAs(CREATE_URL, ME, createJson(RACE, "RACE_VENUE", "제목", "본문"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE"));

		assertThat(inquiryRepository.count()).isZero();
		verify(kafkaTemplate, after(300).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("회원 문의 종류에 주문 ID나 대회 ID가 없으면 400이다")
	void memberTypeNeedsOrderAndRace() throws Exception {
		postAs(CREATE_URL, ME, createJsonOf(RACE, null, "ETC", "제목", "본문")).andExpect(status().isBadRequest());
		postAs(CREATE_URL, ME, createJsonOf(null, ORDER, "ETC", "제목", "본문")).andExpect(status().isBadRequest());
		postAs(CREATE_URL, ME, createJsonOf(null, null, "RACE_VENUE", "제목", "본문")).andExpect(status().isBadRequest());
		postAs(CREATE_URL, ME, createJsonOf(RACE, 0L, "ETC", "제목", "본문")).andExpect(status().isBadRequest());

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("승인된 판매자는 주문·대회 없이 판매자 문의를 보낸다. 담당은 관리자이고 판매자 회원 ID는 비어 있다")
	void sellerSupport() throws Exception {
		postAs(CREATE_URL, APPROVED_SELLER, createJsonOf(null, null, "SELLER_SUPPORT", "정산 문의", "언제 되나요"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.type").value("SELLER_SUPPORT"))
			.andExpect(jsonPath("$.data.assignee").value("ADMIN"))
			.andExpect(jsonPath("$.data.raceId").doesNotExist())
			.andExpect(jsonPath("$.data.orderId").doesNotExist());

		Inquiry saved = inquiryRepository.findAll().getFirst();
		assertThat(saved.getMemberId()).isEqualTo(APPROVED_SELLER);
		assertThat(saved.getAssignee()).isEqualTo(InquiryAssignee.ADMIN);
		assertThat(saved.getSellerMemberId()).isNull();
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.WAITING);
		// 관리자 담당이라 접수 알림은 없다
		verify(kafkaTemplate, after(300).never()).send(any(ProducerRecord.class));
	}

	@Test
	@DisplayName("승인된 판매자가 아니면 판매자 문의는 403이고 저장하지 않는다")
	void sellerSupportForbidden() throws Exception {
		postAs(CREATE_URL, ME, createJsonOf(null, null, "SELLER_SUPPORT", "제목", "본문"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INQUIRY_SELLER_FORBIDDEN"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("회원 서비스가 멈추면 판매자인지 모르므로 판매자 문의는 503이고 저장하지 않는다")
	void sellerSupportUnavailable() throws Exception {
		memberSellerPort.setUnavailable(true);

		postAs(CREATE_URL, APPROVED_SELLER, createJsonOf(null, null, "SELLER_SUPPORT", "제목", "본문"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@ParameterizedTest
	@DisplayName("판매자 문의에 주문 ID나 대회 ID를 넣으면 400이다")
	@ValueSource(strings = {"100, null", "null, 500", "100, 500"})
	void sellerSupportWithOrderOrRace(String ids) throws Exception {
		String[] parts = ids.split(", ");
		Long raceId = "null".equals(parts[0]) ? null : Long.valueOf(parts[0]);
		Long orderId = "null".equals(parts[1]) ? null : Long.valueOf(parts[1]);

		postAs(CREATE_URL, APPROVED_SELLER, createJsonOf(raceId, orderId, "SELLER_SUPPORT", "제목", "본문"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("판매자가 보낸 문의는 내 문의에 보이고, 판매자 목록에는 나오지 않는다")
	void sellerSupportVisibility() throws Exception {
		Inquiry support = saveSellerSupportInquiry(APPROVED_SELLER);

		getAs(BASE + "/inquiries/me", APPROVED_SELLER)
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(support.getId()));
		getAs(BASE + "/seller/inquiries", APPROVED_SELLER)
			.andExpect(jsonPath("$.data.inquiries.length()").value(0));
	}

	@Test
	@DisplayName("관리자만 판매자 문의에 답하고, 판매자 주소로는 답할 수 없다 (404)")
	void sellerSupportAnswer() throws Exception {
		Inquiry support = saveSellerSupportInquiry(APPROVED_SELLER);

		postAs(BASE + "/seller/inquiries/" + support.getId() + "/answer", APPROVED_SELLER, answerJson("답"))
			.andExpect(status().isNotFound());
		postAs(BASE + "/admin/inquiries/" + support.getId() + "/answer", ADMIN, answerJson("다음 주에 돼요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ANSWERED"));
	}
}
