package com.bukang.cash.domain.wallet;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import com.bukang.cash.domain.member.CashMember;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예치금 장부 (지갑 잔액 변동 한 건당 한 줄)
 * 고객 지갑과 홀딩 지갑 사이의 이동은 같은 refTypeCode·refId로 두 줄을 남긴다 (docs/erd/cash.md의 장부 기록 규칙).
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_WALLET_LEDGER")
public class WalletLedger extends BaseIdAndTime {
	@Enumerated(STRING)
	@Column(nullable = false)
	private LedgerType ledgerType;

	@Enumerated(STRING)
	@Column(nullable = false)
	private LedgerRefType refTypeCode;
	private long refId;

	@ManyToOne(fetch = LAZY, optional = false)
	private CashMember holder;
	@ManyToOne(fetch = LAZY, optional = false)
	private Wallet wallet;

	private long amount; // 변동 금액 (+/−)
	private long balanceAfter;

	public WalletLedger(
		Wallet wallet,
		CashMember holder,
		LedgerType ledgerType,
		long amount,
		long balanceAfter,
		LedgerRefType refTypeCode,
		long refId
	) {
		this.wallet = wallet;
		this.holder = holder;
		this.ledgerType = ledgerType;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.refTypeCode = refTypeCode;
		this.refId = refId;
	}
}
