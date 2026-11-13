package com.bukang.payout.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 정산 내역 상태
 * 세미의 지급_완료는 실제 계좌 지급 없이 상태만 바꾸는 모의 지급이다 (docs/policy/payout.md 1장 지급 6번)
 * 취소는 대회가 취소돼 지급하지 않고 닫은 정산 내역이다 (PRO-34, payout.md 4-3 [제안]). 다시 열지 않는다
 * 지급_중은 캐시 서비스에 정산금 이동을 요청한 뒤 결과를 확정하기 전이다 (PRO-252)
 * 요청 전에 커밋해 두므로, 응답을 못 받아 돈이 옮겨졌는지 모를 때도 보류 · 상계 · 취소가 이 정산 내역을 바꾸지 못한다
 * 지급_불일치는 캐시 서비스가 같은 정산 ID를 이미 다른 판매자 · 금액으로 처리했다고 답한(409) 정산 내역이다 (BUK-62)
 * 돈은 캐시 서비스에 저장된 금액으로 나갔다. 지급 배치가 다시 고르지 않고, 보류 · 상계 · 취소도 하지 않는다. 사람이 확인한다
 * 지급_확인_필요는 지급 결과를 3번 몰라(타임아웃 · 5xx) 다시 요청하지 않고 멈춘 정산 내역이다 (BUK-166)
 * 돈이 나갔는지 모르므로 지급_중처럼 보류 · 상계 · 취소를 하지 않고, 대회 취소가 오면 표시만 남기고 회수를 미룬다
 * 지급 배치는 고르지 않는다. 사람이 캐시 서비스에서 결과를 확인해 닫는다 (닫는 API는 BUK-174)
 */
public enum PayoutStatus {
	지급_예정,
	지급보류,
	지급_중,
	지급_완료,
	취소,
	지급_불일치,
	지급_확인_필요;

	// 지급 전: 캐시 서비스로 아직 보내지 않은 상태. 상계 · 대회 취소는 이 상태에서만 정산 내역을 바꾼다
	// 상태를 새로 만들면 여기 넣을지 정한다 (PayoutStatusTest가 분류를 고정한다)
	public static final Set<PayoutStatus> BEFORE_PAYMENT = Collections.unmodifiableSet(EnumSet.of(지급_예정, 지급보류));

	// 지급 결과를 기다림: 돈이 나갔는지 모른다. 대회 취소는 표시만 남기고 회수를 결과가 나올 때까지 미룬다 (PRO-253, BUK-166)
	public static final Set<PayoutStatus> AWAITING_PAY_RESULT = Collections.unmodifiableSet(EnumSet.of(지급_중, 지급_확인_필요));

	public boolean isBeforePayment() {
		return BEFORE_PAYMENT.contains(this);
	}

	public boolean isAwaitingPayResult() {
		return AWAITING_PAY_RESULT.contains(this);
	}
}
