package com.bukang.inquiry.domain;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 대회에 남긴 문의 한 건. 답변은 한 번만 달 수 있고, 같은 행의 컬럼에 저장한다
 * 답변 저장은 InquiryRepository.answerIfWaiting의 조건부 UPDATE가 한다 (동시에 답해도 하나만 저장)
 */
@Entity
@Table(name = "inquiry_inquiry", indexes = {
	@Index(name = "idx_inquiry_member", columnList = "member_id, id"),
	@Index(name = "idx_inquiry_seller", columnList = "seller_member_id, assignee, status, id"),
	@Index(name = "idx_inquiry_status", columnList = "status, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseIdAndTime {
	public static final int MAX_TITLE_LENGTH = 100;
	public static final int MAX_BODY_LENGTH = 1000;
	public static final int MAX_ANSWER_LENGTH = 2000;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	// 판매자 문의(SELLER_SUPPORT)는 대회·주문·판매자 회원이 없다
	@Column(name = "race_id")
	private Long raceId;

	@Column(name = "order_id")
	private Long orderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private InquiryType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private InquiryAssignee assignee;

	// 문의를 만들 때 상품 서비스에서 받아 저장한다. 판매자 권한은 「요청한 회원 = 이 값」으로 확인한다
	@Column(name = "seller_member_id")
	private Long sellerMemberId;

	@Column(nullable = false, length = MAX_TITLE_LENGTH)
	private String title;

	@Column(nullable = false, length = MAX_BODY_LENGTH)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private InquiryStatus status;

	@Column(length = MAX_ANSWER_LENGTH)
	private String answer;

	@Column(name = "answered_by")
	private Long answeredBy;

	@Column(name = "answered_at")
	private LocalDateTime answeredAt;

	// 보낸 회원이 지운 시각. 차 있으면 어느 목록·상세에도 보이지 않는다 (행은 남긴다)
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public static Inquiry create(long memberId, Long raceId, Long orderId, InquiryType type, Long sellerMemberId,
		String title, String body) {
		Inquiry inquiry = new Inquiry();
		inquiry.memberId = memberId;
		inquiry.raceId = raceId;
		inquiry.orderId = orderId;
		inquiry.type = type;
		inquiry.assignee = type.assignee();
		inquiry.sellerMemberId = sellerMemberId;
		inquiry.title = title;
		inquiry.body = body;
		inquiry.status = InquiryStatus.WAITING;
		return inquiry;
	}

	public boolean isSellerOwnedBy(long memberId) {
		return assignee == InquiryAssignee.SELLER && sellerMemberId != null && sellerMemberId == memberId;
	}

	public boolean isAdminOwned() {
		return assignee == InquiryAssignee.ADMIN;
	}
}
