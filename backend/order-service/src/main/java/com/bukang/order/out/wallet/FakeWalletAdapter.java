package com.bukang.order.out.wallet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 예치금 서비스를 띄우지 않고 개발할 때, 잔액이 늘 충분한 것처럼 결제를 성공시킨다
 * order.clients.cash.mode=fake(기본값)일 때만 쓴다. 실제 예치금 서비스는 http로 바꾸면 HttpWalletAdapter가 부른다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.cash", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeWalletAdapter implements WalletPort {
	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeWalletAdapter() {
		log.warn("예치금 결제는 가짜 응답(FakeWalletAdapter)을 씁니다. 실제 예치금 서비스는 order.clients.cash.mode=http");
	}

	@Override
	public WalletPayResult pay(long orderId, long memberId, long amount) {
		log.debug("가짜 예치금 결제 orderId={}, memberId={}, amount={}", orderId, memberId, amount);
		return WalletPayResult.paid();
	}

	@Override
	public void cancelPayment(long orderId, long memberId) {
		log.debug("가짜 예치금 결제 취소 orderId={}, memberId={}", orderId, memberId);
	}

	@Override
	public void refund(long orderId, long memberId) {
		log.debug("가짜 예치금 환불 orderId={}, memberId={}", orderId, memberId);
	}
}
