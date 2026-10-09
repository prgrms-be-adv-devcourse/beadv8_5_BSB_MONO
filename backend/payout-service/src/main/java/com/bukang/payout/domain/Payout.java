package com.bukang.payout.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

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
	private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

	// 정산 ID: ST-정산 내역을 만든 날-16진수 4자리 (예: ST-20261029-0A3F)
	// 끝 4자리를 PK에서 만들어 저장 뒤에 채우므로 NULL을 허용한다
	@Column(unique = true, length = 20)
	private String payoutCode;

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

	// 정산 항목은 추가만 한다. orphanRemoval을 켜면 리스트에서 빼는 것만으로 행이 지워지므로 쓰지 않는다
	@OneToMany(mappedBy = "payout", cascade = ALL)
	private List<PayoutItem> items = new ArrayList<>();

	public Payout(int sellerId, int raceId, YearMonth payoutMonth, LocalDateTime settledAt) {
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.payoutMonth = payoutMonth.toString();
		this.settledAt = settledAt;
		this.status = PayoutStatus.CALCULATED;
	}

	// 바깥에서 remove, clear로 정산 기록을 바꾸지 못하도록 읽기 전용으로 내보낸다 (추가는 addItem으로만)
	public List<PayoutItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	// 정산 항목(PayoutItem)은 정산 내역(Payout)을 통해서만 만든다 (payout_id NOT NULL)
	// 세미는 정산 대상일 전에 환불된 참가권이 빠지므로 판매(SALE)만 기록한다 (payout.md 2-3)
	public PayoutItem addItem(int orderItemId, long price, BigDecimal feeRate, LocalDate targetDate) {
		PayoutItem item = new PayoutItem(this, orderItemId, PayoutRecordType.SALE, price, feeRate, targetDate);
		items.add(item);

		// 수수료는 참가권 1장마다 원 미만을 버리고, 정산 내역의 수수료는 그 합이다 (payout.md 2-1)
		salesAmount += price;
		feeAmount += feeOf(price, feeRate);
		// 정책 공식(판매액 − 환불액 − 수수료)과 같은 모양으로 둔다. 세미는 refundAmount가 항상 0이다:
		// 대상일 전 환불은 항목이 안 생기고, 대상일 후 환불은 막는다(PRO-28). 최종에서는 대상일~지급 사이 환불이
		// 음수 정산 항목(PRO-32), 지급 뒤 환불은 회수(PRO-33)가 된다 (payout.md 2-2, 4-2)
		payoutAmount = salesAmount - refundAmount - feeAmount;
		return item;
	}

	// 저장해서 ID를 받은 뒤 부른다. 하루 65,536건을 넘지 않는 한 같은 날 같은 코드가 나오지 않는다
	public void assignPayoutCode() {
		this.payoutCode = "ST-%s-%04X".formatted(settledAt.format(CODE_DATE), getId() & 0xFFFF);
	}

	private static long feeOf(long price, BigDecimal feeRate) {
		return BigDecimal.valueOf(price).multiply(feeRate).setScale(0, RoundingMode.FLOOR).longValueExact();
	}
}
