package com.bukang.order.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CartItemDto {
	private final long id;
	// 담은 일시
	private final LocalDateTime createDate;
	private final long raceCourseId;
	// 이 종목으로 저장한 결제 전 신청서(PENDING) ID. 장바구니 결제에 보낸다. 신청서 없이 담았으면 null
	// 다른 기기·새로고침에서도 결제할 수 있게 서버가 알려 준다
	private final Long participationId;
	// 신청서의 대회 ID. 장바구니 줄에 대회 정보를 그릴 때 쓴다. 신청서가 없으면 null
	private final Long raceId;
	// 신청서의 참가자 이름. 신청서가 없으면 null
	private final String participantName;
}
