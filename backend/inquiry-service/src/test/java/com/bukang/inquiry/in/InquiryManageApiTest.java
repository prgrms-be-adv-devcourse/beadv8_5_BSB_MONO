package com.bukang.inquiry.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 설계 9장: 판매자·관리자 상세(주문 정보), 목록의 상태 필터·상태별 개수·정렬·닉네임을 확인한다
 */
class InquiryManageApiTest extends InquiryTestBase {
	private static final String SELLER_URL = BASE + "/seller/inquiries";
	private static final String ADMIN_URL = BASE + "/admin/inquiries";

	@Test
	@DisplayName("판매자 상세: 문의와 함께 그 대회 항목만 담은 주문 정보와 문의한 회원 닉네임을 준다")
	void sellerDetail() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		getAs(SELLER_URL + "/" + inquiry.getId(), SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(inquiry.getId()))
			.andExpect(jsonPath("$.data.memberNickname").value("러너하나"))
			.andExpect(jsonPath("$.data.order.orderNo").value("OD-20261110-1"))
			.andExpect(jsonPath("$.data.order.paymentAmount").value(50000))
			.andExpect(jsonPath("$.data.order.status").value("PAID"))
			// 주문에는 대회 두 개가 있지만, 이 문의의 대회(100) 항목만 담는다
			.andExpect(jsonPath("$.data.order.items.length()").value(1))
			.andExpect(jsonPath("$.data.order.items[0].raceId").value(RACE))
			.andExpect(jsonPath("$.data.order.items[0].raceName").value("서울 마라톤 100"))
			.andExpect(jsonPath("$.data.order.items[0].courseName").value("10K"))
			.andExpect(jsonPath("$.data.order.items[0].kitSummary").value("티셔츠 M, 완주 메달"))
			.andExpect(jsonPath("$.data.order.items[0].deliveryStatus").value("PREPARING"));
	}

	@Test
	@DisplayName("판매자 상세: 남의 대회 문의·관리자 담당 문의·없는 문의·지운 문의는 404다")
	void sellerDetailNotAllowed() throws Exception {
		Inquiry otherRace = saveSellerInquiry(ME, OTHER_RACE, OTHER_SELLER);
		Inquiry adminOwned = saveAdminInquiry(ME, RACE, SELLER);
		Inquiry deleted = saveSellerInquiry(ME, RACE, SELLER);
		deleteAs(BASE + "/inquiries/" + deleted.getId(), ME).andExpect(status().isOk());

		getAs(SELLER_URL + "/" + otherRace.getId(), SELLER).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
		getAs(SELLER_URL + "/" + adminOwned.getId(), SELLER).andExpect(status().isNotFound());
		getAs(SELLER_URL + "/" + deleted.getId(), SELLER).andExpect(status().isNotFound());
		getAs(SELLER_URL + "/123456", SELLER).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("상세: 주문 서비스가 멈추면 문의는 그대로 주고 주문 정보만 null이다 (답변이 막히지 않게)")
	void detailWhenOrderServiceDown() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		orderLookupPort.setUnavailable(true);

		getAs(SELLER_URL + "/" + inquiry.getId(), SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(inquiry.getId()))
			.andExpect(jsonPath("$.data.title").value(inquiry.getTitle()))
			.andExpect(jsonPath("$.data.order").doesNotExist());
		getAs(ADMIN_URL + "/" + inquiry.getId(), ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.order").doesNotExist());
	}

	@Test
	@DisplayName("상세: 주문이 이제 없다고 답하면 주문 정보만 null이다")
	void detailWhenOrderGone() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		orderLookupPort.clear();

		getAs(SELLER_URL + "/" + inquiry.getId(), SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.order").doesNotExist());
	}

	@Test
	@DisplayName("상세: 회원 서비스(닉네임)가 멈춰도 문의는 그대로 주고 닉네임만 null이다")
	void detailWhenNicknameDown() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);
		memberNicknamesPort.setUnavailable(true);

		getAs(SELLER_URL + "/" + inquiry.getId(), SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberNickname").doesNotExist())
			.andExpect(jsonPath("$.data.order.orderNo").exists());
	}

	@Test
	@DisplayName("관리자 상세: 판매자 담당 문의도 볼 수 있고, 관리자가 아니면 403, 회원 서비스가 멈추면 503이다")
	void adminDetail() throws Exception {
		Inquiry sellerOwned = saveSellerInquiry(ME, RACE, SELLER);

		getAs(ADMIN_URL + "/" + sellerOwned.getId(), ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(sellerOwned.getId()))
			.andExpect(jsonPath("$.data.order.orderNo").exists());
		getAs(ADMIN_URL + "/" + sellerOwned.getId(), ME)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INQUIRY_ADMIN_FORBIDDEN"));
		getAs(ADMIN_URL + "/123456", ADMIN).andExpect(status().isNotFound());
		memberAdminPort.setUnavailable(true);
		getAs(ADMIN_URL + "/" + sellerOwned.getId(), ADMIN).andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("관리자 상세: 판매자 문의는 주문이 없어 주문 정보가 null이고 주문 서비스를 부르지 않는다")
	void adminDetailOfSellerSupport() throws Exception {
		Inquiry support = saveSellerSupportInquiry(APPROVED_SELLER);
		orderLookupPort.setUnavailable(true);

		getAs(ADMIN_URL + "/" + support.getId(), ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.type").value("SELLER_SUPPORT"))
			.andExpect(jsonPath("$.data.memberNickname").value("판매자셋"))
			.andExpect(jsonPath("$.data.order").doesNotExist());
	}

	@Test
	@DisplayName("판매자 목록: 답변 대기가 먼저, 그 안에서 최신순이다. 상태별 개수와 닉네임을 함께 준다")
	void sellerListOrderCountsNickname() throws Exception {
		Inquiry oldWaiting = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry answered = saveSellerInquiry(OTHER, RACE, SELLER);
		Inquiry newWaiting = saveSellerInquiry(ME, RACE, SELLER);
		// 다른 판매자 문의와 지운 문의는 목록에도 개수에도 들어가지 않는다
		saveSellerInquiry(ME, OTHER_RACE, OTHER_SELLER);
		Inquiry deleted = saveSellerInquiry(ME, RACE, SELLER);
		deleteAs(BASE + "/inquiries/" + deleted.getId(), ME).andExpect(status().isOk());
		postAs(SELLER_URL + "/" + answered.getId() + "/answer", SELLER, answerJson("답")).andExpect(status().isOk());

		getAs(SELLER_URL, SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(3))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(newWaiting.getId()))
			.andExpect(jsonPath("$.data.inquiries[1].id").value(oldWaiting.getId()))
			.andExpect(jsonPath("$.data.inquiries[2].id").value(answered.getId()))
			.andExpect(jsonPath("$.data.inquiries[0].memberNickname").value("러너하나"))
			.andExpect(jsonPath("$.data.inquiries[2].memberNickname").value("러너둘"))
			.andExpect(jsonPath("$.data.waitingCount").value(2))
			.andExpect(jsonPath("$.data.answeredCount").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	@DisplayName("판매자 목록: status로 거르면 그 상태만 나오고, 상태별 개수는 거르기와 상관없이 전체 개수다")
	void sellerListStatusFilter() throws Exception {
		Inquiry waiting = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry answered = saveSellerInquiry(OTHER, RACE, SELLER);
		postAs(SELLER_URL + "/" + answered.getId() + "/answer", SELLER, answerJson("답")).andExpect(status().isOk());

		getAs(SELLER_URL + "?status=WAITING", SELLER)
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(waiting.getId()))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.waitingCount").value(1))
			.andExpect(jsonPath("$.data.answeredCount").value(1));
		getAs(SELLER_URL + "?status=ANSWERED", SELLER)
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(answered.getId()));
		getAs(SELLER_URL + "?status=NOPE", SELLER).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("관리자 목록: 모든 문의를 답변 대기 먼저·최신순으로 주고, 상태 필터와 상태별 개수·닉네임도 같다")
	void adminList() throws Exception {
		Inquiry oldWaiting = saveSellerInquiry(ME, RACE, SELLER);
		Inquiry answered = saveAdminInquiry(OTHER, RACE, SELLER);
		Inquiry support = saveSellerSupportInquiry(APPROVED_SELLER);
		Inquiry deleted = saveAdminInquiry(ME, RACE, SELLER);
		deleteAs(BASE + "/inquiries/" + deleted.getId(), ME).andExpect(status().isOk());
		postAs(ADMIN_URL + "/" + answered.getId() + "/answer", ADMIN, answerJson("답")).andExpect(status().isOk());

		getAs(ADMIN_URL, ADMIN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(3))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(support.getId()))
			.andExpect(jsonPath("$.data.inquiries[1].id").value(oldWaiting.getId()))
			.andExpect(jsonPath("$.data.inquiries[2].id").value(answered.getId()))
			.andExpect(jsonPath("$.data.inquiries[0].memberNickname").value("판매자셋"))
			.andExpect(jsonPath("$.data.waitingCount").value(2))
			.andExpect(jsonPath("$.data.answeredCount").value(1));
		getAs(ADMIN_URL + "?status=ANSWERED", ADMIN)
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].id").value(answered.getId()))
			.andExpect(jsonPath("$.data.waitingCount").value(2));
	}

	@Test
	@DisplayName("목록: 닉네임 조회가 실패하거나 없는 회원이면 목록은 그대로 주고 닉네임만 null이다")
	void listWhenNicknameMissing() throws Exception {
		saveSellerInquiry(ME, RACE, SELLER);
		memberNicknamesPort.setUnavailable(true);

		getAs(SELLER_URL, SELLER)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inquiries.length()").value(1))
			.andExpect(jsonPath("$.data.inquiries[0].memberNickname").doesNotExist());
		memberNicknamesPort.setUnavailable(false);
		saveSellerInquiry(999L, RACE, SELLER);
		getAs(SELLER_URL, SELLER)
			.andExpect(jsonPath("$.data.inquiries[0].memberNickname").doesNotExist())
			.andExpect(jsonPath("$.data.inquiries[1].memberNickname").value("러너하나"));
	}

	@Test
	@DisplayName("내 문의 목록·상세에는 닉네임과 주문 정보를 넣지 않는다 (본인 화면에는 필요 없다)")
	void memberViewsHaveNoManageFields() throws Exception {
		Inquiry inquiry = saveSellerInquiry(ME, RACE, SELLER);

		getAs(BASE + "/inquiries/" + inquiry.getId(), ME)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberNickname").doesNotExist())
			.andExpect(jsonPath("$.data.order").doesNotExist());
		assertThat(inquiryRepository.count()).isEqualTo(1);
	}
}
