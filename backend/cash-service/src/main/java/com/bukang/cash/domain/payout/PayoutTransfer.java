package com.bukang.cash.domain.payout;

import static jakarta.persistence.EnumType.STRING;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 지급 한 번의 이동(대금 또는 수수료) 기록 (PRO-256)
 * payout-service는 하나의 정산 ID(payoutId)만 넘기지만, 대금 이동과 수수료 이동은 받는 지갑이 서로 달라
 * 이 행을 "근거 객체"로 삼아 각자의 id를 WalletLedger의 ref_id로 쓴다(docs/erd/cash.md 장부 기록 규칙).
 * (payoutId, transferType) 유니크 제약으로 같은 이동이 두 번 기록되지 않는다(정산 ID를 멱등키로 쓰는 중복 지급 방지).
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_PAYOUT_TRANSFER", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"payout_id", "transfer_type"})
})
public class PayoutTransfer extends BaseIdAndTime {
	// 정산 서비스(payout-service)의 정산 ID. 다른 서비스의 행이라 FK 없이 값으로만 보관한다
	@Column(nullable = false)
	private long payoutId;

	// 판매자 신청서 ID(= SellerChangedEvent.id). 회원 ID가 아니라서 지갑 조회에는 못 쓴다 (PRO-256 리뷰 반영)
	// 지갑은 CashSupport.resolveSellerMemberId로 바꾼 회원 ID로 찾고, 여기엔 payout-service가 보낸 원래 값을 추적용으로 남긴다
	@Column(nullable = false)
	private long sellerId;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutTransferType transferType;

	@Column(nullable = false)
	private long amount;

	public PayoutTransfer(long payoutId, long sellerId, PayoutTransferType transferType, long amount) {
		this.payoutId = payoutId;
		this.sellerId = sellerId;
		this.transferType = transferType;
		this.amount = amount;
	}
}
