package com.bukang.cash.domain.wallet;

import static jakarta.persistence.FetchType.LAZY;

import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.common.global.jpa.entity.BaseManualIdAndTime;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 예치금 지갑 (회원당 하나)
 * 1번 지갑은 system(플랫폼 수수료 수익), 2번 지갑은 holding(판매자 정산 전 보관)으로 쓴다.
 * 잔액은 credit/debit으로만 바꾸고, 돌려받은 원장(WalletLedger)은 호출한 쪽이 같은 트랜잭션에서 저장한다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_WALLET")
public class Wallet extends BaseManualIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	private CashMember holder;

	private long balance;
	@Version
	private long version;

	public Wallet(CashMember holder) {
		super(holder.getId());
		this.holder = holder;
	}

	public boolean isBalance() {
		return balance > 0;
	}

	public WalletLedger credit(long amount, LedgerType ledgerType, LedgerRefType refTypeCode, long refId) {
		balance += amount;
		return addLedger(amount, ledgerType, refTypeCode, refId);
	}

	public WalletLedger credit(long amount, LedgerType ledgerType, WalletCharge charge) {
		return credit(amount, ledgerType, LedgerRefType.WalletCharge, charge.getId());
	}

	public WalletLedger debit(long amount, LedgerType ledgerType, LedgerRefType refTypeCode, long refId) {
		balance -= amount;
		return addLedger(-amount, ledgerType, refTypeCode, refId);
	}

	public WalletLedger debit(long amount, LedgerType ledgerType, WalletWithdrawal withdrawal) {
		return debit(amount, ledgerType, LedgerRefType.Withdrawal, withdrawal.getId());
	}

	private WalletLedger addLedger(long amount, LedgerType ledgerType, LedgerRefType refTypeCode, long refId) {
		return new WalletLedger(this, holder, ledgerType, amount, balance, refTypeCode, refId);
	}
}
