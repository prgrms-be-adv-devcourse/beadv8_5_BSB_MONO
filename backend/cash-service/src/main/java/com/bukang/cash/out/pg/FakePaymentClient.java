package com.bukang.cash.out.pg;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 클라이언트: PG 서버를 띄우지 않고 개발할 때, 요청받은 금액 그대로 승인된 것으로 답한다
 * cash.clients.pg.mode=fake(기본값)일 때만 쓴다. 실제 Toss는 mode=http로 바꾸면 TossPaymentClient가 부른다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cash.clients.pg", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakePaymentClient implements PaymentClient {
	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakePaymentClient() {
		log.warn("PG 결제 승인은 가짜 응답(FakePaymentClient)을 씁니다. 실제 Toss는 cash.clients.pg.mode=http");
	}

	@Override
	public PaymentApprovalResult approve(String paymentKey, String orderId, long amount) {
		return new PaymentApprovalResult(amount, "fake-payment-key-" + paymentKey);
	}

	@Override
	public Optional<PaymentApprovalResult> findCompletedByOrderId(String orderId) {
		// approve가 항상 성공하는 가짜 클라이언트라, 재조회가 실제로 불릴 일이 없다
		return Optional.empty();
	}
}
