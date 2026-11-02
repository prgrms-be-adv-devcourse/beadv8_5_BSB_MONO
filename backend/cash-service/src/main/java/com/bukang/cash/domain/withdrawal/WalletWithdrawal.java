package com.bukang.cash.domain.withdrawal;

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
 * 예치금 환급(현금 환불) 신청
 * 신청 일시는 createDate로 대신한다. 금액은 신청 시점 잔액 전부다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_WALLET_WITHDRAWAL")
public class WalletWithdrawal extends BaseIdAndTime {
	@ManyToOne(fetch = LAZY, optional = false)
	private Wallet wallet;
	private long amount;

	@Column(nullable = false, length = 10)
	private String bankCode;
	// AES-GCM 암호화(IV 16바이트+태그 16바이트) 후 Base64로 저장하면 평문보다 약 4/3배 길어진다.
	// 실제 계좌번호는 짧지만, 요청 DTO가 최대 255자까지 받으므로 그 길이가 들어와도 되게 넉넉히 둔다
	@Convert(converter = EncryptedStringConverter.class) // DB 저장 시 AES 암호화, 조회 시 복호화
	@Column(nullable = false, length = 512)
	private String accountNumber;
	@Column(nullable = false, length = 50)
	private String accountHolder;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private WithdrawalStatus status;

	@Enumerated(STRING)
	@Column(length = 50) // status가 FAILED일 때만 채운다
	private WithdrawalFailReason failReason;

	@Column(nullable = false, unique = true, length = 64) // 중복 신청 방지
	private String idempotencyKey;
	private LocalDateTime completeDate;

	public WalletWithdrawal(
		Wallet wallet,
		long amount,
		String bankCode,
		String accountNumber,
		String accountHolder,
		String idempotencyKey
	) {
		this.wallet = wallet;
		this.amount = amount;
		this.bankCode = bankCode;
		this.accountNumber = accountNumber;
		this.accountHolder = accountHolder;
		this.idempotencyKey = idempotencyKey;
		this.status = WithdrawalStatus.REQUESTED;
	}

	public void markCompleted(LocalDateTime completeDate) {
		this.status = WithdrawalStatus.COMPLETED;
		this.completeDate = completeDate;
	}
}
