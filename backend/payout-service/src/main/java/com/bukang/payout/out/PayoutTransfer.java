package com.bukang.payout.out;

import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStage;

/**
 * 캐시 서비스에 옮겨 달라고 요청하는 정산금 한 건 (PRO-252)
 * 홀딩 지갑 → 판매자 지갑으로 지급액(대금), 홀딩 지갑 → 시스템 지갑으로 수수료를 옮긴다. 1차 정산은 수수료가 0원이다
 * 상계로 줄어든 몫은 구매자 환불 재원이라 홀딩 지갑에 남는다 (지급액이 이미 그만큼 줄어 있다)
 *
 * @param payoutId     정산 ID(PK). 캐시 서비스가 같은 정산 ID로 돈을 두 번 옮기지 않는 멱등 키로 쓴다
 * @param sellerId     판매자 ID(판매자 신청서 ID, MEMBER_SELLER.seller_id). 회원 ID가 아니다.
 *                     캐시 서비스가 판매자 복제본(CashSeller)으로 지갑 주인(회원 ID)을 찾는다
 * @param payoutAmount 판매자 지갑으로 옮길 지급액. 0원 이상이다
 * @param feeAmount    시스템 지갑으로 옮길 수수료. 0원 이상이다
 * @param stage        1차 / 최종. 캐시 서비스가 장부를 나눠 쓸 때 쓴다
 */
public record PayoutTransfer(long payoutId, long sellerId, long payoutAmount, long feeAmount, PayoutStage stage) {
	public static PayoutTransfer from(Payout payout) {
		return new PayoutTransfer(payout.getId(), payout.getSellerId(), payout.getPayoutAmount(),
			payout.getFeeAmount(), payout.getPayoutStage());
	}
}
