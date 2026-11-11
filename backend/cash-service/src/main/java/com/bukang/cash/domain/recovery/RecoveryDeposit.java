package com.bukang.cash.domain.recovery;

import static jakarta.persistence.EnumType.STRING;

import java.time.LocalDateTime;

import com.bukang.common.global.crypto.EncryptedStringConverter;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회수 입금: 판매자가 갚을 돈(정산의 청구) 하나를 받을 가상계좌와 그 입금 상태
 * 청구 하나에 한 행이다. 청구 ID는 PG 주문번호로도 써서, 입금 알림이 어느 청구 건인지 맞춘다
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_RECOVERY_DEPOSIT")
public class RecoveryDeposit extends BaseIdAndTime {
	// 정산의 청구 ID (예: RC-20261214-0A3F). 같은 청구로 계좌가 두 번 생기지 않게 막는다
	@Column(nullable = false, unique = true, length = 64)
	private String claimCode;

	private long amount;

	@Column(nullable = false, length = 20)
	private String bankName;

	@Column(nullable = false, length = 40)
	private String accountNumber;

	// 입금 기한(한국 시간). 지나면 PG가 계좌를 닫는다
	@Column(nullable = false)
	private LocalDateTime dueDate;

	// 입금 알림 웹훅이 진짜 PG가 보낸 것인지 맞춰 볼 값이라 암호화해 둔다
	@Convert(converter = EncryptedStringConverter.class)
	@Column(nullable = false, length = 256)
	private String secret;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private RecoveryDepositStatus status;

	// PG가 알려 준 입금 시각(한국 시간). 입금 완료일 때만 있다
	private LocalDateTime depositedAt;

	// 정산에 입금 메시지를 보낸 시각. 입금 완료인데 비어 있으면 아직 안 보낸 것이다 (아웃박스 표시)
	private LocalDateTime sentAt;

	public RecoveryDeposit(
		String claimCode, long amount, String bankName, String accountNumber, LocalDateTime dueDate, String secret
	) {
		this.claimCode = claimCode;
		this.amount = amount;
		this.bankName = bankName;
		this.accountNumber = accountNumber;
		this.dueDate = dueDate;
		this.secret = secret;
		this.status = RecoveryDepositStatus.WAITING;
	}

	public void markDeposited(LocalDateTime depositedAt) {
		this.status = RecoveryDepositStatus.DEPOSITED;
		this.depositedAt = depositedAt;
	}

	public void markExpired() {
		this.status = RecoveryDepositStatus.EXPIRED;
	}

	public void markSent(LocalDateTime sentAt) {
		this.sentAt = sentAt;
	}
}
