package com.bukang.payout.domain;

/**
 * 2단계 정산 금액 한 벌 (TwoStagePayoutCalculator의 결과)
 *
 * @param firstPayoutAmount      1차 지급액: 장마다 1차 몫의 합 − 1차 지급 전 환불분(음수 항목)
 * @param recoveryAmount         회수액: 1차 지급 뒤 환불된 참가권의 1차 몫 합 (회수 실행은 PRO-33)
 * @param salesAmount            판매액 (1차 생성 때 살아 있던 참가권)
 * @param refundAmount           환불액 (1차 생성 뒤 환불)
 * @param feeAmount              남은 참가권의 수수료 합 (최종에서만 뗀다)
 * @param sellerSettlementAmount 판매자 정산액 = 순매출 − 수수료 (판매자가 받을 돈 전부)
 * @param advanceDeductionAmount 1차 정산금 차감 = 1차 지급액 − 회수액 (손에 남은 1차 정산금)
 * @param finalPayoutAmount      최종 지급액 = 판매자 정산액 − 1차 정산금 차감
 */
public record TwoStageAmounts(long firstPayoutAmount, long recoveryAmount, long salesAmount, long refundAmount,
	long feeAmount, long sellerSettlementAmount, long advanceDeductionAmount, long finalPayoutAmount) {

	static TwoStageAmounts of(long firstPayout, long recovery, long sales, long refund, long fee) {
		long sellerSettlement = sales - refund - fee;
		long advanceDeduction = firstPayout - recovery;
		// 지름길(순매출 × 60% − 수수료)로 계산하지 않는다. 1차를 장마다 버린 차이를 최종이 메워야 해서 몇 원 다르다
		return new TwoStageAmounts(firstPayout, recovery, sales, refund, fee, sellerSettlement, advanceDeduction,
			sellerSettlement - advanceDeduction);
	}

	public long netSalesAmount() {
		return salesAmount - refundAmount;
	}
}
