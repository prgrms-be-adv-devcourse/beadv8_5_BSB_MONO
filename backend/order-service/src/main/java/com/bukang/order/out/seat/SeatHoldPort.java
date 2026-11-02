package com.bukang.order.out.seat;

import java.time.LocalDateTime;

/**
 * 상품 서비스(B)에 참가권 자리 1석을 확보한다. 자리 행(RACE_SEAT)과 정원은 B 소유다 (ERD 추천안 v4, 팀 결정 #10)
 * 결제 기한(개인 15분)은 주문이 정해 보내고 B가 그 시각까지 자리를 잡는다. 결제와 만료 판정의 원본은 B에 저장된 expires_at이다
 * 답이 필요한 확보·판매만 HTTP로 부른다. 판매 자리 복구(product.hold.restore)와 결제 안 한 자리 해제(product.hold.cancel)는
 * 답을 기다리지 않는 알림이라 OrderOutboxKafkaAppendUseCase가 취소·만료 트랜잭션 안에서 아웃박스에 남기고 커밋 뒤 보낸다 (PRO-270)
 * B의 복구·해제는 참가 신청 ID로 멱등이다: 같은 메시지가 두 번 와도 한 번만 되돌리고, 판매되지 않은(복구)·이미 판매된(해제) 자리는 그대로 둔다
 */
public interface SeatHoldPort {
	// 결제 기한(expiresAt)은 주문이 정해 보낸다 (10/14 김호빈님과 정함, 대기열이 생기면 활성 시간이 끝나는 시각)
	// 멱등: 같은 참가 신청 ID로 다시 부르면 새 자리를 잡지 않고 처음 결과(처음 기한)를 돌려준다
	SeatHoldResult hold(long raceCourseId, long participationId, LocalDateTime expiresAt);

	// 결제 뒤 자리를 판매 완료로 바꾼다. 기한이 지났으면 B가 거절한다 (결제·만료 판정 원본은 B의 expires_at)
	// 멱등: 이미 판매한 자리면 기한과 상관없이 SOLD다. 판매 뒤 주문 저장이 롤백되어 같은 주문으로 다시 결제할 때,
	// 기한이 지났다고 EXPIRED를 받으면 예치금은 돌려주는데 자리는 팔린 채로 남는다
	SeatSellResult sell(long participationId);
}
