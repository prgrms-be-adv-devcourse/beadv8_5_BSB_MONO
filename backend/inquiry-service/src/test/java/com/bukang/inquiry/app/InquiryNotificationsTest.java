package com.bukang.inquiry.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryDraft;
import com.bukang.inquiry.domain.InquiryType;

class InquiryNotificationsTest {
	private static Inquiry inquiry(long memberId, Long sellerMemberId, InquiryType type) {
		Inquiry inquiry = Inquiry.create(new InquiryDraft(memberId, sellerMemberId, null, null, type, "주차는요", "본문"));
		ReflectionTestUtils.setField(inquiry, "id", 9L);
		return inquiry;
	}

	@Test
	@DisplayName("받은 알림은 판매자에게 가고 문구와 링크가 정해진 대로다")
	void received() {
		NotificationRequestedEvent event = InquiryNotifications.received(inquiry(1L, 5L, InquiryType.RACE_VENUE));

		assertThat(event.memberId()).isEqualTo(5L);
		assertThat(event.kind()).isEqualTo(NotificationKind.INQUIRY_RECEIVED);
		assertThat(event.title()).isEqualTo("새 문의가 왔어요");
		assertThat(event.body()).isEqualTo("주차는요\n문의에 답변해 주세요.");
		assertThat(event.href()).isEqualTo("/seller/inquiries/9");
	}

	@Test
	@DisplayName("답변 알림은 문의한 회원에게 가고, 판매자 문의는 판매자 콘솔 링크다")
	void answered() {
		NotificationRequestedEvent buyer = InquiryNotifications.answered(inquiry(1L, 5L, InquiryType.RACE_VENUE));
		NotificationRequestedEvent seller =
			InquiryNotifications.answered(inquiry(2L, null, InquiryType.SELLER_SUPPORT));

		assertThat(buyer.memberId()).isEqualTo(1L);
		assertThat(buyer.kind()).isEqualTo(NotificationKind.INQUIRY_ANSWERED);
		assertThat(buyer.title()).isEqualTo("문의에 답변이 달렸어요");
		assertThat(buyer.body()).isEqualTo("주차는요\n답변을 확인해 보세요.");
		assertThat(buyer.href()).isEqualTo("/me/inquiries/9");
		assertThat(seller.href()).isEqualTo("/seller/inquiries/9?filter=SENT");
	}
}
