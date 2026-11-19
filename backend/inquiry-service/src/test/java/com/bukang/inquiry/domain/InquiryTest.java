package com.bukang.inquiry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InquiryTest {
	@Test
	@DisplayName("InquiryDraft로 만들면 값이 순서가 아니라 이름대로 들어간다")
	void createFromDraft() {
		Inquiry inquiry = Inquiry.create(new InquiryDraft(1L, 2L, 3L, 4L, InquiryType.RACE_VENUE, "제목", "본문"));

		assertThat(inquiry.getMemberId()).isEqualTo(1L);
		assertThat(inquiry.getSellerMemberId()).isEqualTo(2L);
		assertThat(inquiry.getRaceId()).isEqualTo(3L);
		assertThat(inquiry.getOrderId()).isEqualTo(4L);
		assertThat(inquiry.getAssignee()).isEqualTo(InquiryAssignee.SELLER);
		assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.WAITING);
	}

	@Test
	@DisplayName("판매자 담당 종류만 isSellerAssigned가 true다")
	void sellerAssigned() {
		assertThat(Inquiry.create(new InquiryDraft(1L, 2L, 3L, null, InquiryType.RACE_VENUE, "t", "b"))
			.isSellerAssigned()).isTrue();
		assertThat(Inquiry.create(new InquiryDraft(1L, 2L, 3L, null, InquiryType.ETC, "t", "b"))
			.isSellerAssigned()).isFalse();
	}

	@Test
	@DisplayName("isOwnedBy는 문의를 보낸 회원만 true다")
	void ownedBy() {
		Inquiry inquiry = Inquiry.create(new InquiryDraft(1L, 2L, 3L, null, InquiryType.ETC, "t", "b"));

		assertThat(inquiry.isOwnedBy(1L)).isTrue();
		assertThat(inquiry.isOwnedBy(2L)).isFalse();
	}
}
