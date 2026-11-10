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

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutTransferStatus status;

	// 은행 송금을 몇 번째로 시도하는지. 은행은 같은 송금 ID를 한 번만 보내므로, 실패 뒤 다시 보낼 때 ID를 바꾸는 데 쓴다
	@Column(nullable = false)
	private int attempt;

	public PayoutTransfer(long payoutId, long sellerId, PayoutTransferType transferType, long amount) {
		this.payoutId = payoutId;
		this.sellerId = sellerId;
		this.transferType = transferType;
		this.amount = amount;
		// 수수료는 지갑끼리 옮겨 바로 끝나고, 대금은 은행 송금 결과를 기다린다
		this.status = transferType == PayoutTransferType.FEE ? PayoutTransferStatus.COMPLETED
			: PayoutTransferStatus.REQUESTED;
		this.attempt = 1;
	}

	// 은행에 보내는 송금 ID. 같은 시도는 같은 ID라 결과를 모를 때 다시 물어봐도 두 번 나가지 않는다
	public String bankTransferId() {
		return "payout-" + getId() + "-" + attempt;
	}

	public void markCompleted() {
		this.status = PayoutTransferStatus.COMPLETED;
	}
}
