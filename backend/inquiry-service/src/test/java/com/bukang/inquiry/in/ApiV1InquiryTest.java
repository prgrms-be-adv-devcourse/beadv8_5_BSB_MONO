package com.bukang.inquiry.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 문의 API 6개의 권한·검증·오류 코드를 확인한다. 테이블은 Flyway SQL로 만든다(ddl-auto: validate)
 * 동시 답변은 InquiryAnswerConcurrencyTest, 알림은 InquiryNotificationTest에서 본다
 */
class ApiV1InquiryTest extends InquiryTestBase {
	private static final String ME_URL = BASE + "/inquiries/me";
	private static final String CREATE_URL = BASE + "/inquiries";
	private static final String SELLER_URL = BASE + "/seller/inquiries";
	private static final String ADMIN_URL = BASE + "/admin/inquiries";

	@ParameterizedTest(name = "{0} → {1} 담당")
	@DisplayName("문의 종류로 담당이 정해지고, 대회의 판매자 회원 ID가 함께 저장된다")
	@CsvSource({"APPLY_PAYMENT, ADMIN", "RACE_VENUE, SELLER", "KIT_DELIVERY, SELLER", "ETC, ADMIN"})
	void createAssignsByType(String type, String assignee) throws Exception {
		postAs(CREATE_URL, ME, createJson(RACE, type, "제목", "본문"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.type").value(type))
			.andExpect(jsonPath("$.data.assignee").value(assignee))
			.andExpect(jsonPath("$.data.status").value("WAITING"))
			.andExpect(jsonPath("$.data.answer").doesNotExist());

		Inquiry saved = inquiryRepository.findAll().getFirst();
		assertThat(saved.getMemberId()).isEqualTo(ME);
		assertThat(saved.getRaceId()).isEqualTo(RACE);
		assertThat(saved.getSellerMemberId()).isEqualTo(SELLER);
		assertThat(saved.getAssignee()).isEqualTo(InquiryAssignee.valueOf(assignee));
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.WAITING);
	}

	@Test
	@DisplayName("주문에는 있지만 상품 서비스가 모르는 대회에는 문의를 만들 수 없다 (404), 아무것도 저장하지 않는다")
	void createRaceNotFound() throws Exception {
		orderLookupPort.order(ORDER_WITH_UNKNOWN_RACE, orderOf(ME, NO_RACE));

		postAs(CREATE_URL, ME, createJsonOf(NO_RACE, ORDER_WITH_UNKNOWN_RACE, "ETC", "제목", "본문"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_RACE_NOT_FOUND"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("상품 서비스가 멈추면 판매자를 모르므로 503이고 문의를 저장하지 않는다")
	void createProductUnavailable() throws Exception {
		raceSellerPort.setUnavailable(true);

		postAs(CREATE_URL, ME, createJson(RACE, "RACE_VENUE", "제목", "본문"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("제목 100자·본문 1000자는 저장되고, 하나라도 넘으면 400이다")
	void createLengthLimits() throws Exception {
		postAs(CREATE_URL, ME, createJson(RACE, "ETC", "가".repeat(100), "나".repeat(1000)))
			.andExpect(status().isCreated());
		postAs(CREATE_URL, ME, createJson(RACE, "ETC", "가".repeat(101), "본문"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		postAs(CREATE_URL, ME, createJson(RACE, "ETC", "제목", "나".repeat(1001)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(inquiryRepository.count()).isEqualTo(1);
	}

	@ParameterizedTest
	@DisplayName("빈 제목·빈 본문·없는 종류·0 이하 대회 ID·빈 요청은 400이고 저장하지 않는다")
	@ValueSource(strings = {
		"{\"raceId\": 100, \"type\": \"ETC\", \"title\": \" \", \"body\": \"본문\"}",
		"{\"raceId\": 100, \"type\": \"ETC\", \"title\": \"제목\", \"body\": \"\"}",
		"{\"raceId\": 100, \"type\": \"NOPE\", \"title\": \"제목\", \"body\": \"본문\"}",
		"{\"raceId\": 0, \"type\": \"ETC\", \"title\": \"제목\", \"body\": \"본문\"}",
		"{\"type\": \"RACE_VENUE\", \"title\": \"제목\", \"body\": \"본문\"}",
		"{}",
		"{not json"
	})
	void createInvalid(String json) throws Exception {
		postAs(CREATE_URL, ME, json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 모든 주소가 401이다")
	void noMemberHeader() throws Exception {
		mockMvc.perform(get(ME_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(CREATE_URL + "/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(SELLER_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ADMIN_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(CREATE_URL).contentType("application/json")
			.content(createJson(RACE, "ETC", "제목", "본문"))).andExpect(status().isUnauthorized());
		mockMvc.perform(post(SELLER_URL + "/1/answer").contentType("application/json")
			.content(answerJson("답"))).andExpect(status().isUnauthorized());
		mockMvc.perform(post(ADMIN_URL + "/1/answer").contentType("application/json")
			.content(answerJson("답"))).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("내 문의 목록은 내 것만 최신순으로 주고 페이지 정보를 함께 준다")
	void findMine() throws Exception {
		Inquiry first = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry second = saveAdminInquiry(ME, RACE, SELLER);
		saveSellerInquiry(OTHER, RACE, SELLER);

		getAs(ME_URL, ME)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(2))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(second.getId()))
			.andExpect(jsonPath("$.data.inquiries[1].id").value(first.getId()))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(1));
		getAs(ME_URL + "?page=1&size=1", ME)
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(first.getId()))
			.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	@Test
	@DisplayName("페이지 크기가 100을 넘거나 1 미만이면 400이다")
	void invalidPage() throws Exception {
		getAs(ME_URL + "?size=101", ME).andExpect(status().isBadRequest());
		getAs(ME_URL + "?size=0", ME).andExpect(status().isBadRequest());
		getAs(ME_URL + "?page=-1", ME).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("문의 상세는 내 문의만 볼 수 있다. 남의 문의와 없는 문의는 똑같이 404다")
	void findOne() throws Exception {
		Inquiry mine = saveSellerInquiry(ME, RACE, SELLER);

		getAs(CREATE_URL + "/" + mine.getId(), ME)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(mine.getId()))
			.andExpect(jsonPath("$.data.title").value(mine.getTitle()))
			.andExpect(jsonPath("$.data.body").value(mine.getBody()));
		getAs(CREATE_URL + "/" + mine.getId(), OTHER)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
		getAs(CREATE_URL + "/" + (mine.getId() + 1000), ME)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
	}

	@Test
	@DisplayName("문의 ID가 숫자가 아니면 400이다")
	void invalidId() throws Exception {
		getAs(CREATE_URL + "/abc", ME).andExpect(status().isBadRequest());
		postAs(SELLER_URL + "/abc/answer", SELLER, answerJson("답")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("판매자 목록은 저장된 판매자 회원이 나인 판매자 담당 문의만 보여 준다")
	void sellerList() throws Exception {
		Inquiry mine = saveSellerInquiry(ME, RACE, SELLER);
		saveAdminInquiry(ME, RACE, SELLER);
		saveSellerInquiry(ME, OTHER_RACE, OTHER_SELLER);

		getAs(SELLER_URL, SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(mine.getId()));
		getAs(SELLER_URL, ME)
			.andExpect(jsonPath("$.data.inquiries.length()").value(0));
	}

	@Test
	@DisplayName("판매자가 답하면 답변 완료가 되고, 문의한 회원이 상세에서 답변을 본다")
	void sellerAnswer() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		postAs(SELLER_URL + "/" + inquiry.getId() + "/answer", SELLER, answerJson("지도는 홈페이지에 있어요."))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ANSWERED"))
			.andExpect(jsonPath("$.data.answer").value("지도는 홈페이지에 있어요."));

		Inquiry saved = inquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
		assertThat(saved.getAnswer()).isEqualTo("지도는 홈페이지에 있어요.");
		assertThat(saved.getAnsweredBy()).isEqualTo(SELLER);
		assertThat(saved.getAnsweredAt()).isNotNull();
		getAs(CREATE_URL + "/" + inquiry.getId(), ME)
			.andExpect(jsonPath("$.data.answer").value("지도는 홈페이지에 있어요."))
			.andExpect(jsonPath("$.data.answeredAt").exists());
	}

	@Test
	@DisplayName("남의 대회 문의, 관리자 담당 문의, 없는 문의에 판매자가 답하면 404이고 저장하지 않는다")
	void sellerAnswerNotAllowed() throws Exception {
		Inquiry otherRace = saveSellerInquiry(ME, OTHER_RACE, OTHER_SELLER);
		Inquiry adminOwned = saveAdminInquiry(ME, RACE, SELLER);

		postAs(SELLER_URL + "/" + otherRace.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
		postAs(SELLER_URL + "/" + adminOwned.getId() + "/answer", SELLER, answerJson("답"))
			.andExpect(status().isNotFound());
		postAs(SELLER_URL + "/123456/answer", SELLER, answerJson("답"))
			.andExpect(status().isNotFound());
		// 문의를 남긴 본인이어도 판매자 주소로는 답할 수 없다
		postAs(SELLER_URL + "/" + otherRace.getId() + "/answer", ME, answerJson("답"))
			.andExpect(status().isNotFound());

		assertThat(inquiryRepository.findAll()).allSatisfy(i -> assertThat(i.getStatus())
			.isEqualTo(InquiryStatus.WAITING));
	}

	@Test
	@DisplayName("이미 답한 문의에 다시 답하면 409이고 처음 답변이 그대로 남는다")
	void alreadyAnswered() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		postAs(SELLER_URL + "/" + inquiry.getId() + "/answer", SELLER, answerJson("처음 답변")).andExpect(status().isOk());

		postAs(SELLER_URL + "/" + inquiry.getId() + "/answer", SELLER, answerJson("두 번째 답변"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getAnswer()).isEqualTo("처음 답변");
	}

	@Test
	@DisplayName("답변은 비어 있으면 400이고, 2000자는 되고 2001자는 400이다")
	void answerValidation() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		String url = SELLER_URL + "/" + inquiry.getId() + "/answer";

		postAs(url, SELLER, answerJson(" ")).andExpect(status().isBadRequest());
		postAs(url, SELLER, "{}").andExpect(status().isBadRequest());
		postAs(url, SELLER, answerJson("가".repeat(2001))).andExpect(status().isBadRequest());
		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getStatus())
			.isEqualTo(InquiryStatus.WAITING);

		postAs(url, SELLER, answerJson("가".repeat(2000))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("관리자가 아니면 관리자 주소는 403이고, 답변은 저장되지 않는다")
	void adminForbidden() throws Exception {
		Inquiry inquiry = saveAdminInquiry(ME, RACE, SELLER);

		getAs(ADMIN_URL, ME)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INQUIRY_ADMIN_FORBIDDEN"));
		postAs(ADMIN_URL + "/" + inquiry.getId() + "/answer", ME, answerJson("답"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INQUIRY_ADMIN_FORBIDDEN"));
		// 판매자여도 관리자는 아니다
		getAs(ADMIN_URL, SELLER).andExpect(status().isForbidden());

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getStatus())
			.isEqualTo(InquiryStatus.WAITING);
	}

	@Test
	@DisplayName("회원 서비스가 멈추면 관리자인지 모르므로 503이다 (관리자로 보지 않는다)")
	void adminCheckUnavailable() throws Exception {
		Inquiry inquiry = saveAdminInquiry(ME, RACE, SELLER);
		memberAdminPort.setUnavailable(true);

		getAs(ADMIN_URL, ADMIN)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("INQUIRY_EXTERNAL_SERVICE_UNAVAILABLE"));
		postAs(ADMIN_URL + "/" + inquiry.getId() + "/answer", ADMIN, answerJson("답"))
			.andExpect(status().isServiceUnavailable());

		assertThat(inquiryRepository.findById(inquiry.getId()).orElseThrow().getStatus())
			.isEqualTo(InquiryStatus.WAITING);
	}

	@Test
	@DisplayName("관리자는 담당과 상관없이 모든 문의를 최신순으로 본다")
	void adminList() throws Exception {
		Inquiry first = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry second = saveAdminInquiry(OTHER, OTHER_RACE, OTHER_SELLER);

		getAs(ADMIN_URL, ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(2))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(second.getId()))
			.andExpect(jsonPath("$.data.inquiries[1].id").value(first.getId()));
	}

	@Test
	@DisplayName("관리자는 관리자 담당 문의에 답하고, 판매자 담당 문의에 답하면 404다")
	void adminAnswer() throws Exception {
		Inquiry adminOwned = saveAdminInquiry(ME, RACE, SELLER);
		Inquiry sellerOwned = saveSellerInquiry(ME, RACE, SELLER);

		postAs(ADMIN_URL + "/" + adminOwned.getId() + "/answer", ADMIN, answerJson("환불 처리했어요."))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ANSWERED"));
		postAs(ADMIN_URL + "/" + sellerOwned.getId() + "/answer", ADMIN, answerJson("답"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
		postAs(ADMIN_URL + "/" + adminOwned.getId() + "/answer", ADMIN, answerJson("또 답"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

		assertThat(inquiryRepository.findById(adminOwned.getId()).orElseThrow().getAnsweredBy()).isEqualTo(ADMIN);
		assertThat(inquiryRepository.findById(sellerOwned.getId()).orElseThrow().getStatus())
			.isEqualTo(InquiryStatus.WAITING);
	}
}
