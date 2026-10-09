package com.bukang.boundedcontext.payout.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import com.bukang.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 내역: 판매자 × 대회 × 월로 한 번에 지급하는 묶음
 * 판매자, 대회는 다른 도메인이라 연관관계 없이 ID만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
// 정산 생성 배치가 두 번 돌아도 같은 묶음이 두 번 지급되지 않도록 DB에서 막는다
@Table(name = "PAYOUT_PAYOUT", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"seller_id", "race_id", "payout_month"})
})
public class Payout extends BaseIdAndTime {
	@Column(nullable = false)
	private int sellerId;

	@Column(nullable = false)
	private int raceId;

	// YYYY-MM. 정산 대상일이 속한 달
	@Column(nullable = false, length = 7)
	private String payoutMonth;

	@Column(nullable = false)
	private long salesAmount;

	@Column(nullable = false)
	private long refundAmount;

	@Column(nullable = false)
	private long feeAmount;

	@Column(nullable = false)
	private long payoutAmount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutStatus status;

	@Column(nullable = false)
	private LocalDateTime settledAt;

	private LocalDateTime paidAt;

	@OneToMany(mappedBy = "payout", cascade = ALL, orphanRemoval = true)
	private List<PayoutItem> items = new ArrayList<>();

	public Payout(int sellerId, int raceId, YearMonth payoutMonth, LocalDateTime settledAt) {
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.payoutMonth = payoutMonth.toString();
		this.settledAt = settledAt;
		this.status = PayoutStatus.CALCULATED;
	}

	// 정산 항목은 정산 내역을 통해서만 만든다 (payout_id NOT NULL)
	public PayoutItem addItem(
		int orderItemId,
		PayoutRecordType recordType,
		long amount,
		BigDecimal feeRate,
		LocalDate targetDate
	) {
		PayoutItem item = new PayoutItem(this, orderItemId, recordType, amount, feeRate, targetDate);
		items.add(item);
		return item;
	}
}
