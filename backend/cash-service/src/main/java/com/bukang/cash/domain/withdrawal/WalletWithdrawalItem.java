package com.bukang.cash.domain.withdrawal;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환급 한 번 안의 카드 몫(충전 건 하나를 PG 결제취소하는 몫).
 * 결과를 모르는 몫을 재확인이 이어받을 수 있게 PG 중복 방지 키를 함께 남긴다
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_WALLET_WITHDRAWAL_ITEM")
public class WalletWithdrawalItem extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	private WalletWithdrawal withdrawal;

	@ManyToOne(fetch = LAZY, optional = false)
	private WalletCharge charge;

	private long amount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private WithdrawalItemStatus status;

	// 처음 요청과 재확인이 같은 키를 써야 PG가 한 번만 취소한다 (토스는 300자까지, 15일 동안 유효)
	@Column(nullable = false, length = 100)
	private String pgIdempotencyKey;

	public WalletWithdrawalItem(
		WalletWithdrawal withdrawal, WalletCharge charge, long amount, WithdrawalItemStatus status,
		String pgIdempotencyKey
	) {
		this.withdrawal = withdrawal;
		this.charge = charge;
		this.amount = amount;
		this.status = status;
		this.pgIdempotencyKey = pgIdempotencyKey;
	}

	public void markCompleted() {
		this.status = WithdrawalItemStatus.COMPLETED;
	}

	public void markReverted() {
		this.status = WithdrawalItemStatus.REVERTED;
	}

	public void markTransferredToAccount() {
		this.status = WithdrawalItemStatus.TRANSFERRED_TO_ACCOUNT;
	}
}
