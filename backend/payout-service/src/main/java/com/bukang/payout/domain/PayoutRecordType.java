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
	선금(PayoutAccount.선금),
	// 최종 생성 때 참가권마다: 판매 +가격, 수수료 −(가격 × 5.5%, 원 미만 버림)
	판매(PayoutAccount.번_돈),
	수수료(PayoutAccount.번_돈),
	// 최종 생성 때 참가권 없는 한 줄: 그때 남은 선금 잔고 전부를 뺀다 (0원이어도 남긴다)
	선지급_차감(PayoutAccount.선금),
	// 회수 요청 때 두 줄: 1차 정산금에서 빼고, 같은 금액을 갚을 돈으로 옮긴다
	회수_요청(PayoutAccount.선금),
	갚을_돈_추가(PayoutAccount.갚을_돈),
	// 지급 전 정산 내역에서 갚을 돈을 뺀 줄 (상계). 그 정산 내역에 붙는다
	회수_상계(PayoutAccount.갚을_돈),
	// 판매자 입금을 확인한 줄
	회수_입금(PayoutAccount.갚을_돈),
	// 지급 전 정산 내역을 닫을 때 원래 줄을 되돌린다
	선금_취소(PayoutAccount.선금),
	판매_취소(PayoutAccount.번_돈),
	수수료_취소(PayoutAccount.번_돈),
	선지급_차감_취소(PayoutAccount.선금),
	회수_상계_취소(PayoutAccount.갚을_돈),
	// 대회 취소로 1차 지급 전에 상계로 끝난 회수를 되돌린다. 회수 요청 때의 두 줄을 반대 부호로 쓴다 (R9)
	회수_요청_취소(PayoutAccount.선금),
	갚을_돈_추가_취소(PayoutAccount.갚을_돈);

	private final PayoutAccount account;

	// 정산 내역을 닫을 때 쓰는 반대 줄 종류. 회수 줄(요청 · 입금)은 정산 내역에 붙지 않으므로 되돌릴 일이 없다
	public PayoutRecordType canceled() {
		return switch (this) {
			case 선금 -> 선금_취소;
			case 판매 -> 판매_취소;
			case 수수료 -> 수수료_취소;
			case 선지급_차감 -> 선지급_차감_취소;
			case 회수_상계 -> 회수_상계_취소;
			default -> throw new IllegalStateException("되돌릴 수 없는 원장 줄입니다. recordType=%s".formatted(this));
		};
	}
}
