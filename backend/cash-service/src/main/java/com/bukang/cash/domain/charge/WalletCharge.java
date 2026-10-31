package com.bukang.cash.domain.charge;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;

import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.common.global.crypto.EncryptedStringConverter;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PG사를 통한 예치금 충전 요청·승인 내역
 * 요청 일시는 createDate로 대신한다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_WALLET_CHARGE")
public class WalletCharge extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	private Wallet wallet;

	private long amount;

	@Column(nullable = false, length = 30)
	private String pgProvider;

	// AES-GCM 암호화(IV 16바이트+태그 16바이트) 후 Base64로 저장하면 평문보다 약 4/3배 길어진다.
	// Toss paymentKey 최대 길이(200자) 기준 암호문은 312자라, 넉넉히 512로 둔다
	@Convert(converter = EncryptedStringConverter.class) // DB 저장 시 AES 암호화, 조회 시 복호화
	@Column(length = 512)
	private String pgPaymentKey;
	@Column(nullable = false, unique = true, length = 64) // 중복 충전 방지
	private String idempotencyKey;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private ChargeStatus status;
	private LocalDateTime chargeDate;

	public WalletCharge(Wallet wallet, long amount, String pgProvider, String idempotencyKey) {
		this.wallet = wallet;
		this.amount = amount;
		this.pgProvider = pgProvider;
		this.idempotencyKey = idempotencyKey;
		this.status = ChargeStatus.REQUESTED;
	}
	// 지갑 락 안에서 이 orderId를 선점했다는 표시. 이 상태인 동안 다른 요청은 Toss를 다시 부르지 않는다
	public void markInProgress() {
		this.status = ChargeStatus.IN_PROGRESS;
	}

	public void markCompleted(String pgPaymentKey, LocalDateTime chargeDate) {
		this.pgPaymentKey = pgPaymentKey;
		this.status = ChargeStatus.COMPLETED;
		this.chargeDate = chargeDate;
	}

	public void markFailed() {
		this.status = ChargeStatus.FAILED;
	}
}
