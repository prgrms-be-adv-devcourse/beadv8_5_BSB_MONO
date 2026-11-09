package com.bukang.payout.out;

/**
 * 지급 배치가 예치금을 가진 서비스(캐시 서비스)에 정산금 이동을 요청하는 계약 (포트, PRO-252)
 * 지금은 동기 HTTP 어댑터가 구현한다. Kafka · 트랜잭션 아웃박스로 바꾸는 일은 PRO-253에서 다룬다
 *
 * 캐시 서비스와의 약속 (PRO-256 CashPayoutUseCase로 확인). 지급 배치가 정산 내역을 되돌릴지 · 지급 중으로 둘지가 여기에 기댄다
 * - 같은 정산 ID · 같은 판매자 · 같은 금액으로 다시 요청하면 지갑을 건드리지 않고 2xx로 답한다
 * - 같은 정산 ID인데 판매자 · 금액이 이미 처리한 값과 다르면 지갑을 건드리지 않고 409와 저장된 금액으로 답한다.
 *   돈은 저장된 금액으로 이미 나간 것이다 (cash PayoutTransferMismatchException, BUK-62)
 * - 그 밖의 4xx는 돈을 옮기기 전에만 낸다: 요청 검증 실패(400), 지갑 없음(404). 그래서 409가 아닌 4xx면 돈이 안 나간 것이 확실하다
 * 이 약속이 바뀌면 어댑터의 결과 분류도 함께 바꿔야 한다
 */
@FunctionalInterface
public interface PayoutTransferPort {
	/**
	 * 정산금을 옮긴다. 같은 정산 ID · 같은 금액으로 다시 불러도 캐시 서비스는 돈을 한 번만 옮기고 성공으로 답한다
	 *
	 * @throws PayoutTransferRejectedException 캐시 서비스가 거절해 돈이 옮겨지지 않은 것이 확실할 때 (409가 아닌 4xx)
	 * @throws PayoutTransferMismatchException 같은 정산 ID가 이미 다른 판매자 · 금액으로 지급됐을 때 (409)
	 * @throws PayoutTransferUnknownException  응답을 못 받았거나 서버 오류라 돈이 옮겨졌는지 모를 때
	 */
	void transfer(PayoutTransfer transfer);
}
