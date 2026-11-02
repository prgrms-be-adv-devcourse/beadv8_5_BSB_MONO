package com.bukang.cash.domain.payment;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;

import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예치금으로 하는 주문 결제
 * 시도(요청) 일시는 createDate로 대신한다.
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_PAYMENT")
public class Payment extends BaseIdAndTime {
	// 주문은 다른 서비스의 테이블이라 연관관계(FK) 없이 ID 값으로만 보관한다, 주문 id를 멱등 키로 사용
	@Column(nullable = false, unique = true)
	private long orderId;

	@ManyToOne(fetch = LAZY, optional = false)
	private Wallet wallet;

	private long amount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;
	@Enumerated(STRING)
	@Column(length = 50) // status가 FAILED일 때만 채운다
	private PaymentFailReason failReason;

	private LocalDateTime completeDate;

	public Payment(long orderId, Wallet wallet, long amount) {
		this.orderId = orderId;
		this.wallet = wallet;
		this.amount = amount;
		this.status = PaymentStatus.PENDING;
	}

	public void markCompleted(LocalDateTime completeDate) {
		this.status = PaymentStatus.COMPLETED;
		this.completeDate = completeDate;
		this.failReason = null;
	}

	public void markFailed(PaymentFailReason failReason) {
		this.status = PaymentStatus.FAILED;
		this.failReason = failReason;
	}

	public void markCanceled() {
		this.status = PaymentStatus.CANCELED;
		this.failReason = null;
	}

	public void markRefunded() {
		this.status = PaymentStatus.REFUNDED;
		this.failReason = null;
	}

	// completeDate는 markCompleted에서만 채워지므로, 지금 상태(CANCELED 등)와 상관없이
	// 이 결제가 한 번이라도 COMPLETED를 거쳐 실제로 돈이 움직인 적 있는지를 말해준다 (PRO-170)
	public boolean wasCompleted() {
		return completeDate != null;
	}
}
