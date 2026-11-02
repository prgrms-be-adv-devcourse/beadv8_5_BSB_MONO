package com.bukang.order.out.wallet;

/**
 * 예치금 서비스(D)에 결제(차감)·결제 취소·환불을 요청한다. 잔액·원장·결제 기록(Payment)은 D 소유다 (D PRO-77~80, PRO-83)
 * 세 요청 모두 (주문 ID, 회원 ID)로 보낸다. D는 회원 ID로 고객 지갑을 먼저 잠그고, 주문 ID로 멱등 처리한다 (10/13 김소희님 합의)
 * 응답 시간 초과는 실패가 아니라 "모름"이다. D가 이미 처리했을 수 있으므로 주문을 끝내지 않고,
 * 같은 주문 ID로 다시 부르거나(결제) 만료 처리에서 결제 취소를 부른다 (OrderExpiryUseCase)
 */
public interface WalletPort {
	// 같은 주문은 한 번만 차감한다. 이미 결제된 주문이면 차감 없이 PAID, 잔액이 모자라면 부족 금액을 준다 (D PRO-79)
	WalletPayResult pay(long orderId, long memberId, long amount);

	// 그 주문의 결제를 되돌린다. 몇 번, 언제 불러도 안전하다:
	// 결제됐으면 돌려주고, 결제 전이면 막아 두어 뒤늦게 온 결제를 거절하고, 이미 취소·환불됐으면 그냥 성공한다 (D PRO-80)
	void cancelPayment(long orderId, long memberId);

	// 결제한 참가권을 취소하면 그 주문의 결제 금액 전액을 돌려준다. 금액은 D가 결제 기록으로 정하고, 주문마다 한 번만 환불한다
	// 부분 취소가 없고 주문 하나 = 참가권 하나라 주문 단위로 충분하다 (D PRO-83)
	void refund(long orderId, long memberId);
}
