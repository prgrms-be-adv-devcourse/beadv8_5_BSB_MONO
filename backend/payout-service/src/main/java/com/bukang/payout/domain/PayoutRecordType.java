package com.bukang.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 원장 줄 종류 (PRO-214). 종류마다 계정이 정해져 있어, 줄을 만들 때 계정을 따로 고르지 않는다
 * 한 사건이 두 계정에 줄을 쓰면(회수 요청) 종류 이름을 달리해 (order_item_id, record_type) 유일 제약에 걸리지 않게 한다
 * 취소(_CANCELED)는 정산 내역을 닫을 때 원래 줄의 반대 부호로 쓴다 (PRO-34)
 */
@Getter
@RequiredArgsConstructor
public enum PayoutRecordType {
	// 1차 생성 때 코스마다 한 줄(참가권 없는 줄): 장마다 가격 × 40%(원 미만 버림)의 합
	ADVANCE_PAID(PayoutAccount.ADVANCE, "선금"),
	// 최종 생성 때 참가권마다: 판매 +가격, 수수료 −(가격 × 5.5%, 원 미만 버림)
	SALE(PayoutAccount.EARNING, "판매"),
	FEE(PayoutAccount.EARNING, "수수료"),
	// 최종 생성 때 참가권 없는 한 줄: 그때 남은 ADVANCE 잔고 전부를 뺀다 (0원이어도 남긴다)
	ADVANCE_OFFSET(PayoutAccount.ADVANCE, "선지급 차감"),
	// 회수 요청 때 두 줄: 1차 정산금에서 빼고, 같은 금액을 갚을 돈으로 옮긴다
	RECOVERY_REQUESTED(PayoutAccount.ADVANCE, "회수 요청"),
	RECEIVABLE_ADDED(PayoutAccount.RECEIVABLE, "갚을 돈 추가"),
	// 지급 전 정산 내역에서 갚을 돈을 뺀 줄 (상계). 그 정산 내역에 붙는다
	RECOVERY_OFFSET(PayoutAccount.RECEIVABLE, "회수 상계"),
	// 판매자 입금을 확인한 줄
	RECOVERY_RECEIVED(PayoutAccount.RECEIVABLE, "회수 입금"),
	// 지급 전 정산 내역을 닫을 때 원래 줄을 되돌린다
	ADVANCE_PAID_CANCELED(PayoutAccount.ADVANCE, "선금 취소"),
	SALE_CANCELED(PayoutAccount.EARNING, "판매 취소"),
	FEE_CANCELED(PayoutAccount.EARNING, "수수료 취소"),
	ADVANCE_OFFSET_CANCELED(PayoutAccount.ADVANCE, "선지급 차감 취소"),
	RECOVERY_OFFSET_CANCELED(PayoutAccount.RECEIVABLE, "회수 상계 취소");

	private final PayoutAccount account;
	private final String korean;

	// 정산 내역을 닫을 때 쓰는 반대 줄 종류. 회수 줄(요청 · 입금)은 정산 내역에 붙지 않으므로 되돌릴 일이 없다
	public PayoutRecordType canceled() {
		return switch (this) {
			case ADVANCE_PAID -> ADVANCE_PAID_CANCELED;
			case SALE -> SALE_CANCELED;
			case FEE -> FEE_CANCELED;
			case ADVANCE_OFFSET -> ADVANCE_OFFSET_CANCELED;
			case RECOVERY_OFFSET -> RECOVERY_OFFSET_CANCELED;
			default -> throw new IllegalStateException("되돌릴 수 없는 원장 줄입니다. recordType=%s".formatted(this));
		};
	}
}
