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
	// PG가 승인한 시각(한국 시간). PG 결제취소 기한의 기준이다. 이 칸을 더하기 전에 완료된 충전은 null이다
	private LocalDateTime approvedAt;
	// 이 충전에서 PG 결제취소로 이미 돌려준 금액. 부분 취소를 여러 번 하면 더해진다
	private long canceledAmount;
	// 신규 계정 고액 충전 검토 대상 표시 (정책 4장 2차 2-1). 가입 후 24시간 안에 누적 충전이
	// 한도를 처음 넘긴 충전 건에만 선다. 충전을 막지는 않는다
	private boolean reviewFlagged;

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

	public void markCompleted(String pgPaymentKey, LocalDateTime chargeDate, LocalDateTime approvedAt) {
		this.pgPaymentKey = pgPaymentKey;
		this.status = ChargeStatus.COMPLETED;
		this.chargeDate = chargeDate;
		this.approvedAt = approvedAt;
	}

	// PG 결제취소 기한의 기준 시각. 승인 시각이 없는 예전 충전은 서버가 저장한 충전 시각으로 대신한다
	public LocalDateTime approvedAtOrChargeDate() {
		return approvedAt != null ? approvedAt : chargeDate;
	}

	// 충전 금액보다 많이 돌려주면 같은 돈을 두 번 돌려주는 것이라, 호출하는 쪽 버그로 보고 막는다
	public void recordCancel(long amount) {
		if (canceledAmount + amount > this.amount) {
			throw new IllegalArgumentException(
				"충전 금액보다 많이 취소할 수 없습니다. chargeId=" + getId() + ", 남은 금액=" + (this.amount - canceledAmount)
					+ ", 요청 금액=" + amount);
		}
		this.canceledAmount += amount;
	}

	public void markFailed() {
		this.status = ChargeStatus.FAILED;
	}

	public void markUncertain() {
		this.status = ChargeStatus.UNCERTAIN;
	}

	public void markReviewFlagged() {
		this.reviewFlagged = true;
	}
}
