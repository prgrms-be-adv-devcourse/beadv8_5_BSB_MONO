package com.bukang.inquiry.app;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;

/**
 * 문의 알림의 문구와 이동 링크를 한곳에 모은다. 보내는 일(트랜잭션 안에서 publish)은 부르는 쪽이 한다
 */
final class InquiryNotifications {
	private InquiryNotifications() {
	}

	// 판매자 담당 문의가 생기면 그 대회 판매자에게 알린다
	static NotificationRequestedEvent received(Inquiry inquiry) {
		return NotificationRequestedEvent.of(inquiry.getSellerMemberId(), NotificationKind.INQUIRY_RECEIVED,
			"새 문의가 왔어요", inquiry.getTitle() + "\n문의에 답변해 주세요.", "/seller/inquiries/" + inquiry.getId());
	}

	static NotificationRequestedEvent answered(Inquiry inquiry) {
		// 판매자가 관리자에게 보낸 문의는 구매자 화면이 아니라 판매자 콘솔의 보낸 문의 화면으로 연결한다
		String href = inquiry.getType() == InquiryType.SELLER_SUPPORT
			? "/seller/inquiries/" + inquiry.getId() + "?filter=SENT"
			: "/me/inquiries/" + inquiry.getId();
		return NotificationRequestedEvent.of(inquiry.getMemberId(), NotificationKind.INQUIRY_ANSWERED,
			"문의에 답변이 달렸어요", inquiry.getTitle() + "\n답변을 확인해 보세요.", href);
	}
}
