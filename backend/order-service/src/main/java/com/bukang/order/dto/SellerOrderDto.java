package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.OrderItemStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;

/**
 * 판매자 주문 목록의 한 줄 (BUK-227, 시안 S03·S05). 주문 상품(참가권 1장) 하나다
 * 참가자 연락처·생년월일·배송지는 넣지 않는다. 배송지는 키트 배송 목록(배송에 필요한 범위)에서만 보여 준다
 */
@Getter
public class SellerOrderDto {
	private final long orderItemId;
	private final long orderId;
	private final String orderNo;
	// 주문한 시각과 결제한 시각(결제 전이면 null)
	private final LocalDateTime orderedAt;
	private final LocalDateTime paidAt;
	private final long raceId;
	private final long raceCourseId;
	// 주문 당시 종목 이름
	private final String courseName;
	private final OrderItemStatus status;
	private final String participantName;
	// 옛 티셔츠 사이즈 칸. 키트 옵션 전에 결제한 신청만 쓴다
	private final String tshirtSize;
	// 결제 때 고른 키트 옵션 사본 (고른 순). 키트 옵션이 없는 참가권은 비어 있다. 목록을 읽은 뒤 한 번에 채운다
	private List<KitOptionChoiceDto> kitOptions = List.of();
	// 파티로 신청했으면 파티 ID, 개인 신청이면 null
	private final Long partyId;
	@JsonIgnore
	private final long participationId;

	// JPQL 생성자 조회용
	public SellerOrderDto(long orderItemId, long orderId, String orderNo, LocalDateTime orderedAt,
		LocalDateTime paidAt, long raceId, long raceCourseId, String courseName, OrderItemStatus status,
		String participantName, String tshirtSize, Long partyId, long participationId) {
		this.orderItemId = orderItemId;
		this.orderId = orderId;
		this.orderNo = orderNo;
		this.orderedAt = orderedAt;
		this.paidAt = paidAt;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.courseName = courseName;
		this.status = status;
		this.participantName = participantName;
		this.tshirtSize = tshirtSize;
		this.partyId = partyId;
		this.participationId = participationId;
	}

	public void attachKitOptions(List<KitOptionChoiceDto> kitOptions) {
		this.kitOptions = kitOptions;
	}
}
