package com.bukang.cash.out.pg;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
		return new PaymentApprovalResult(amount, "fake-payment-key-" + paymentKey, LocalDateTime.now());
	}

	// 가짜 PG가 발급한 가상계좌의 금액 (주문번호 → 금액). 토스 없이도 회수 입금 흐름을 끝까지 보려고 기억한다
	// shortcut: 메모리에만 두어 앱을 다시 켜면 잊는다. 그 전에 발급한 계좌는 입금 대기로 남는다 (가짜 모드에서만)
	private final Map<String, Long> issuedVirtualAccounts = new ConcurrentHashMap<>();

	// 가짜 PG가 발급한 가상계좌는 바로 입금된 것으로 답한다. 웹훅을 흉내 내거나 10분 재확인이 돌면 입금으로 기록된다
	// 충전은 approve가 항상 성공해 재조회가 불릴 일이 없으므로, 발급하지 않은 주문번호는 지금처럼 승인 안 됨으로 답한다
	@Override
	public PaymentLookupResult lookupByOrderId(String orderId) {
		Long depositedAmount = issuedVirtualAccounts.get(orderId);
		if (depositedAmount == null) {
			return PaymentLookupResult.notCompleted();
		}
		return PaymentLookupResult.completed(
			new PaymentApprovalResult(depositedAmount, "fake-payment-key-" + orderId, LocalDateTime.now()));
	}

	@Override
	public PaymentCancelResult cancel(
		String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey
	) {
		return new PaymentCancelResult(cancelAmount, "fake-cancel-" + paymentKey);
	}

	@Override
	public VirtualAccountIssueResult issueVirtualAccount(String orderId, long amount) {
		issuedVirtualAccounts.put(orderId, amount);
		// Toss 테스트 계좌처럼 X로 시작해 가짜임을 알 수 있게 한다
		return new VirtualAccountIssueResult(
			"국민은행", "X-fake-" + orderId, amount, LocalDateTime.now().plusDays(90), "fake-secret-" + orderId);
	}

	@Override
	public Optional<VirtualAccountIssueResult> findVirtualAccount(String orderId) {
		// 가짜 발급은 항상 성공해 결제 쪽에 바로 저장되므로, PG에만 남은 계좌는 없다
		return Optional.empty();
	}
}
