package com.bukang.payout.domain;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.payout.domain.exception.PayoutHoldConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 내역: 판매자 × 대회 × 월 × 단계(1차 / 최종)로 한 번에 지급하는 묶음 (payout.md 4-3)
 * 같은 달이어도 1차와 최종은 따로 만든다. 판매자, 대회는 다른 도메인이라 연관관계 없이 ID만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
// 정산 생성 배치가 두 번 돌아도 같은 묶음이 두 번 지급되지 않도록 DB에서 막는다
@Table(name = "PAYOUT_PAYOUT", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"seller_id", "race_id", "payout_month", "payout_stage"})
})
public class Payout extends BaseIdAndTime {
	private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
	// 정기 지급일: 정산 월의 다음 달 20일 (휴일이면 다음 영업일, payout.md 2-4)
	private static final int PAY_DAY_OF_MONTH = 20;

	// 정산 ID: ST-정산 내역을 만든 날-16진수 4자리 (예: ST-20261029-0A3F)
	// 끝 4자리를 PK에서 만들어 저장 뒤에 채우므로 NULL을 허용한다
	@Column(unique = true, length = 20)
	private String payoutCode;

	@Column(nullable = false)
	private int sellerId;

	@Column(nullable = false)
	private int raceId;

	// 정산 내역을 만들 때 상품 서비스에서 받은 대회 이름. 영수증처럼 정산한 때의 이름을 남긴다
	// 판매자 조회 때 상품 서비스를 부르지 않으려고 저장한다 (정산은 대회 다음 날 만들어져 그 뒤 이름이 바뀔 일은 드물다)
	@Column(nullable = false, length = 200)
	private String raceName;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutStage payoutStage;

	// YYYY-MM. 1차는 환불 마감일이 속한 달, 최종은 대회일이 속한 달 (실행일 · 정산 대상일이 아니다)
	@Column(nullable = false, length = 7)
	private String payoutMonth;

	@Column(nullable = false)
	private long salesAmount;

	@Column(nullable = false)
	private long refundAmount;

	@Column(nullable = false)
	private long feeAmount;

	// 선지급 차감: 최종에서 빼는 ADVANCE 잔고 (1차 선금 − 회수 요청액, 손에 남은 선금). 1차는 0이다 (payout.md 4-4)
	// 만들 때의 값이고, 같은 금액의 ADVANCE_OFFSET 원장 줄이 함께 생긴다
	@Column(nullable = false)
	private long advanceDeductionAmount;

	// 지급액: 이 묶음 원장 줄의 합 (캐시). 상계 줄이 더해지면 줄고, 취소하면 0이 된다
	@Column(nullable = false)
	private long payoutAmount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutStatus status;

	@Column(nullable = false)
	private LocalDateTime settledAt;

	// 지급 예정일. 지급 배치는 이 날이 지난 CALCULATED만 지급한다
	// 보류를 풀 때(PRO-26) 해제일 뒤 돌아오는 지급일(20일)로 다시 정하므로, 배치는 보류 기록을 보지 않아도 된다
	@Column(nullable = false)
	private LocalDate scheduledPayDate;

	private LocalDateTime paidAt;

	// 낙관적 락: 지급 배치와 보류 API가 같은 정산 내역을 동시에 바꾸면 늦게 커밋한 쪽이 실패한다
	// 없으면 배치가 읽은 CALCULATED를 근거로, 그 사이 걸린 HELD를 PAID로 덮어쓸 수 있다
	@Version
	private long version;

	// 정산 항목은 추가만 한다. orphanRemoval을 켜면 리스트에서 빼는 것만으로 행이 지워지므로 쓰지 않는다
	@OneToMany(mappedBy = "payout", cascade = ALL)
	private List<PayoutItem> items = new ArrayList<>();

	// 상태 이력도 추가만 한다. 상태를 바꾸는 메서드가 changeStatus로 한 줄씩 남겨 빠뜨릴 수 없게 한다 (PRO-34)
	// LAZY라 상태를 바꿀 때 이력 목록을 한 번 읽는다. 그래서 상태 변경은 트랜잭션(세션) 안에서만 한다
	@OneToMany(mappedBy = "payout", cascade = ALL)
	private List<PayoutStatusHistory> statusHistories = new ArrayList<>();

	private Payout(int sellerId, int raceId, String raceName, PayoutStage payoutStage, YearMonth payoutMonth,
		long advanceDeductionAmount, LocalDateTime settledAt, BusinessDayCalendar calendar) {
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.raceName = raceName;
		this.payoutStage = payoutStage;
		this.payoutMonth = payoutMonth.toString();
		this.advanceDeductionAmount = advanceDeductionAmount;
		this.settledAt = settledAt;
		this.scheduledPayDate = payDayOf(payoutMonth.plusMonths(1), calendar);
		// 정산 내역은 배치가 만든다
		changeStatus(PayoutStatus.CALCULATED, PayoutHold.SYSTEM_MEMBER_ID, settledAt, null);
		// 최종은 그때 남은 선금(ADVANCE 잔고)을 한 줄로 뺀다. 0원이어도 남겨 "뺄 선금이 없었다"를 기록한다 (PRO-214)
		if (payoutStage == PayoutStage.FINAL) {
			addLine(PayoutRecordType.ADVANCE_OFFSET, null, null, -advanceDeductionAmount, null, null);
		}
	}

	// 1차 정산 내역: 정산 월은 환불 마감일의 달이다
	public static Payout first(int sellerId, int raceId, String raceName, YearMonth payoutMonth,
		LocalDateTime settledAt, BusinessDayCalendar calendar) {
		return new Payout(sellerId, raceId, raceName, PayoutStage.FIRST, payoutMonth, 0L, settledAt, calendar);
	}

	// 최종 정산 내역: 정산 월은 대회일의 달이고, 선지급 차감은 만들 때의 ADVANCE 잔고(1차 선금 − 회수 요청액)다
	public static Payout finalStage(int sellerId, int raceId, String raceName, YearMonth payoutMonth,
		long advanceDeductionAmount, LocalDateTime settledAt, BusinessDayCalendar calendar) {
		return new Payout(sellerId, raceId, raceName, PayoutStage.FINAL, payoutMonth, advanceDeductionAmount,
			settledAt, calendar);
	}

	// 바깥에서 remove, clear로 원장 줄을 바꾸지 못하도록 읽기 전용으로 내보낸다 (추가는 Payout의 메서드로만)
	public List<PayoutItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	public List<PayoutStatusHistory> getStatusHistories() {
		return Collections.unmodifiableList(statusHistories);
	}

	/**
	 * 1차 선금 줄(ADVANCE_PAID)을 코스마다 한 줄씩 쓴다. 참가권 목록 없이 판매 집계(가격 합 · 장수)만으로 계산한다 (PRO-214)
	 * 코스 하나는 참가권 가격이 하나다. 그래서 가격 합 ÷ 장수가 1장 가격이고,
	 * 선금 = DOWN(1장 가격 × 40%) × 장수는 장마다 40%를 버려 더한 값(정책 Q7)과 같다. 회수 몫(1장 × 40%)과도 정확히 맞는다
	 * 가격 합이 장수로 나누어떨어지지 않으면 가격이 섞인 것이므로 계산하지 않고 실패로 남긴다
	 */
	public PayoutItem recordAdvance(int raceCourseId, long priceSum, int ticketCount, LocalDate targetDate) {
		if (payoutStage != PayoutStage.FIRST) {
			throw new IllegalStateException("선금은 1차 정산에만 씁니다. payoutStage=%s".formatted(payoutStage));
		}
		if (ticketCount <= 0 || priceSum % ticketCount != 0) {
			throw new IllegalStateException(
				"한 코스의 참가권 가격이 하나가 아닙니다. raceId=%d, raceCourseId=%d, priceSum=%d, ticketCount=%d"
					.formatted(raceId, raceCourseId, priceSum, ticketCount));
		}
		boolean alreadyRecorded = items.stream()
			.anyMatch(item -> item.getRecordType() == PayoutRecordType.ADVANCE_PAID
				&& Integer.valueOf(raceCourseId).equals(item.getRaceCourseId()));
		if (alreadyRecorded) {
			throw new IllegalStateException(
				"선금 줄은 코스마다 하나만 씁니다. raceId=%d, raceCourseId=%d".formatted(raceId, raceCourseId));
		}
		salesAmount += priceSum;
		long share = TwoStagePayoutCalculator.firstShare(priceSum / ticketCount) * ticketCount;
		return addLine(PayoutRecordType.ADVANCE_PAID, null, raceCourseId, share, null, targetDate);
	}

	// 최종 정산에 참가권 1장을 넣는다. 원장 줄은 정산 내역(Payout)을 통해서만 만든다 (금액 캐시와 함께 바뀌어야 하므로)
	// 환불 마감일까지 환불된 참가권은 판매 내역에서 빠지므로 판매만 기록한다 (payout.md 2-3)
	// 참가권마다 판매(+가격)와 수수료(−가격 × 5.5%, 원 미만 버림) 두 줄을 쓰고, 코스별로 볼 수 있게 코스를 적는다
	// 지급액은 언제나 이 묶음 원장 줄의 합이다
	public PayoutItem addItem(int orderItemId, Integer raceCourseId, long price, BigDecimal feeRate,
		LocalDate targetDate) {
		if (payoutStage != PayoutStage.FINAL) {
			throw new IllegalStateException("1차 정산은 참가권 줄 없이 recordAdvance로 만듭니다. raceId=%d".formatted(raceId));
		}
		salesAmount += price;
		long fee = TwoStagePayoutCalculator.rate(price, feeRate);
		feeAmount += fee;
		PayoutItem sale = addLine(PayoutRecordType.SALE, orderItemId, raceCourseId, price, feeRate, targetDate);
		addLine(PayoutRecordType.FEE, orderItemId, raceCourseId, -fee, feeRate, targetDate);
		return sale;
	}

	// 코스를 모를 때(테스트 · 샘플 데이터)
	public PayoutItem addItem(int orderItemId, long price, BigDecimal feeRate, LocalDate targetDate) {
		return addItem(orderItemId, null, price, feeRate, targetDate);
	}

	/**
	 * 송금 전 묶음에서 판매자가 갚을 돈을 뺀다 (회수 상계, PRO-214)
	 * 묶음 금액까지만 뺀다. 묶음은 0 아래로 내려가지 않고, 남은 갚을 돈은 RECEIVABLE에 남아 입금으로 받는다
	 *
	 * @param receivable 같은 판매자 × 대회의 갚을 돈(RECEIVABLE 잔고)
	 * @return 이번에 뺀 금액. 뺄 게 없으면 0이고 줄을 쓰지 않는다
	 */
	public long offsetReceivable(long receivable) {
		if (status != PayoutStatus.CALCULATED && status != PayoutStatus.HELD) {
			throw new IllegalStateException("송금 전 정산 내역에서만 상계할 수 있습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		long offset = Math.min(receivable, payoutAmount);
		if (offset <= 0) {
			return 0;
		}
		addLine(PayoutRecordType.RECOVERY_OFFSET, null, null, -offset, null, null);
		return offset;
	}

	private PayoutItem addLine(PayoutRecordType recordType, Integer orderItemId, Integer raceCourseId, long amount,
		BigDecimal feeRate, LocalDate targetDate) {
		PayoutItem item = PayoutItem.ofPayout(this, recordType, orderItemId, raceCourseId, amount, feeRate,
			targetDate);
		items.add(item);
		payoutAmount += amount;
		return item;
	}

	// 저장해서 ID를 받은 뒤 부른다. 하루 65,536건을 넘지 않는 한 같은 날 같은 코드가 나오지 않는다
	public void assignPayoutCode() {
		this.payoutCode = "ST-%s-%04X".formatted(settledAt.format(CODE_DATE), getId() & 0xFFFF);
	}

	// 세미의 지급 완료는 실제 송금 없이 상태만 바꾸는 모의 지급이다 (payout.md 1장 지급 6번)
	// 보류된 정산 내역은 지급 완료로 바꿀 수 없다. 배치가 CALCULATED만 고르지만, 잘못 불려도 여기서 막는다
	public void pay(LocalDateTime paidAt) {
		if (status != PayoutStatus.CALCULATED) {
			throw new IllegalStateException("정산 예정 상태가 아니라 지급할 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		// 지급은 지급 배치가 한다
		changeStatus(PayoutStatus.PAID, PayoutHold.SYSTEM_MEMBER_ID, paidAt, null);
		this.paidAt = paidAt;
	}

	// 지급보류 걸기: 정산 예정(CALCULATED)일 때만 건다. 세미는 활성 보류를 하나만 둔다 (PRO-26)
	// 보류 기록(PayoutHold)은 여기서만 만든다. 저장은 호출한 쪽이 한다
	public PayoutHold hold(PayoutHoldReason reasonType, String reason, int heldBy, LocalDateTime heldAt) {
		if (status == PayoutStatus.HELD) {
			throw new PayoutHoldConflictException("이미 지급보류 중인 정산 내역입니다.");
		}
		if (status == PayoutStatus.PAID) {
			throw new PayoutHoldConflictException("이미 지급 완료된 정산 내역입니다.");
		}
		if (status == PayoutStatus.CANCELED) {
			throw new PayoutHoldConflictException("취소된 정산 내역입니다.");
		}
		changeStatus(PayoutStatus.HELD, heldBy, heldAt, reasonType.getKorean());
		return new PayoutHold(this, reasonType, reason, heldBy, heldAt);
	}

	// 지급보류 풀기: 정산 예정으로 돌리고 지급 예정일을 해제일 뒤 돌아오는 지급일로 다시 정한다 (payout.md 2-6)
	// 단, 원래 지급 예정일보다 앞당기지 않는다 (PRO-128). 보류를 걸 때는 지급 예정일을 바꾸지 않으므로
	// 지금 값이 원래 지급 예정일(또는 이전 해제로 이미 늦춰진 날)이다
	// 지급 배치는 상태와 지급 예정일만 보므로, 보류 기록을 보지 않아도 다음 지급일에 지급된다
	public void release(PayoutHold hold, int releasedBy, LocalDateTime releasedAt, BusinessDayCalendar calendar) {
		// ==로 비교하지 않는다. LAZY로 읽은 hold.getPayout()은 Hibernate 프록시라 같은 행이어도 this와 다른 객체다
		// 프록시의 getId()는 DB를 읽지 않고 들고 있는 ID를 돌려준다
		if (hold.getPayout().getId() != getId()) {
			throw new IllegalArgumentException("이 정산 내역의 지급보류가 아닙니다. payoutId=%d".formatted(getId()));
		}
		// 대회 취소로 닫힌 정산 내역에는 보류 기록이 HELD로 남아 있을 수 있다. 풀어도 지급 예정으로 돌리지 않는다
		if (status == PayoutStatus.CANCELED) {
			throw new PayoutHoldConflictException("취소된 정산 내역의 지급보류는 풀 수 없습니다.");
		}
		hold.markReleased(releasedBy, releasedAt);
		changeStatus(PayoutStatus.CALCULATED, releasedBy, releasedAt, null);
		LocalDate payDateAfterRelease = nextPayDateAfter(releasedAt.toLocalDate(), calendar);
		if (payDateAfterRelease.isAfter(scheduledPayDate)) {
			this.scheduledPayDate = payDateAfterRelease;
		}
	}

	// 대회 취소로 닫기: 지급 전(CALCULATED · HELD) 정산 내역을 CANCELED로 바꿔 다시는 지급되지 않게 한다 (PRO-34)
	// 원장 줄은 지우지 않고 반대 부호 줄을 더해 되돌린다(PRO-214). 그래서 조건 없이 더해도 선금 · 갚을 돈 잔고가 맞는다
	// 지급액(줄의 합)은 0이 되고, 판매액 · 수수료 칸은 만들 때 그대로 둔다(얼마가 나갈 뻔했는지 남긴다)
	// 보류 중이었으면 보류 기록은 HELD로 두고 상태만 최신으로 바꾼다. 무엇이었는지는 상태 이력에 남는다
	// 이미 지급한 정산 내역은 닫지 않는다. 지급 뒤 대회 취소는 회수(PRO-33)가 맡는다
	public void cancel(int canceledBy, LocalDateTime canceledAt, String reason) {
		if (status == PayoutStatus.PAID) {
			throw new IllegalStateException("이미 지급 완료된 정산 내역은 취소할 수 없습니다. payoutId=%d".formatted(getId()));
		}
		if (status == PayoutStatus.CANCELED) {
			throw new IllegalStateException("이미 취소된 정산 내역입니다. payoutId=%d".formatted(getId()));
		}
		List<PayoutItem> original = List.copyOf(items);
		original.forEach(item -> addLine(item.getRecordType().canceled(), item.getOrderItemId(),
			item.getRaceCourseId(), -item.getAmount(), item.getFeeRate(), item.getTargetDate()));
		changeStatus(PayoutStatus.CANCELED, canceledBy, canceledAt, reason);
	}

	private void changeStatus(PayoutStatus to, int changedBy, LocalDateTime changedAt, String reason) {
		statusHistories.add(new PayoutStatusHistory(this, status, to, changedBy, changedAt, reason));
		this.status = to;
	}

	// date "뒤"의 지급일: 이번 달 지급일이 date보다 늦으면 그날, 아니면(당일 포함) 다음 달 지급일
	// 20일이 주말이면 지급일이 밀리므로, 20일이 아니라 밀린 날과 비교한다 (12/20 일 해제 → 12/21 월)
	// 보류 해제와 판매자 요약(지급 예정 정산 내역이 없을 때의 다음 정산일)이 같은 규칙을 쓴다
	public static LocalDate nextPayDateAfter(LocalDate date, BusinessDayCalendar calendar) {
		YearMonth month = YearMonth.from(date);
		LocalDate thisMonthPayDay = payDayOf(month, calendar);
		if (thisMonthPayDay.isAfter(date)) {
			return thisMonthPayDay;
		}
		return payDayOf(month.plusMonths(1), calendar);
	}

	private static LocalDate payDayOf(YearMonth month, BusinessDayCalendar calendar) {
		return calendar.nextOrSame(month.atDay(PAY_DAY_OF_MONTH));
	}
}
