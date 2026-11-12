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
import java.util.Optional;
import java.util.stream.Stream;

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
 * 정산 내역: 판매자 × 대회 × 월 × 단계(1차 / 최종)로 한 번에 지급하는 단위 (payout.md 4-3)
 * 같은 달이어도 1차와 최종은 따로 만든다. 판매자, 대회는 다른 도메인이라 연관관계 없이 ID만 둔다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
// 정산 생성 배치가 두 번 돌아도 같은 정산 내역이 두 번 지급되지 않도록 DB에서 막는다
@Table(name = "PAYOUT_PAYOUT", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"seller_id", "race_id", "payout_month", "payout_stage"})
})
public class Payout extends BaseIdAndTime {
	private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
	// 지급일: 정산 월(1차는 환불 마감일의 달, 최종은 대회일의 달)의 다음 달 20일 (10/29 결정)
	// 주말이어도 그날 지급한다(11/2 결정, 보류 해제 뒤 지급일과 같게 휴일 보정 없음). 지급 배치가 그날 04:00에 지급한다
	private static final int PAY_DAY_OF_MONTH = 20;
	// 보류를 풀면 해제일 다음 날 지급한다 (원래 지급 예정일보다 앞당기지는 않는다)
	private static final int PAY_DAYS_AFTER_RELEASE = 1;
	// 지급 결과를 이만큼 모르면 다시 요청하지 않고 지급_확인_필요로 멈춘다 (BUK-166, 11/11 결정)
	public static final int MAX_UNKNOWN_PAY = 3;

	// 정산 ID: ST-정산 내역을 만든 날-16진수 4자리 (예: ST-20261029-0A3F)
	// 정산 생성일이 아니라 만든 날이다. 놓친 날을 따라잡아 만든 정산 내역은 만든 날이 정산 생성일보다 늦다 (PRO-120)
	// 끝 4자리를 PK에서 만들어 저장 뒤에 채우므로 NULL을 허용한다
	@Column(unique = true, length = 20)
	private String payoutCode;

	@Column(nullable = false)
	private long sellerId;

	@Column(nullable = false)
	private long raceId;

	// 정산 내역을 만들 때 상품 서비스에서 받은 대회 이름. 영수증처럼 정산한 때의 이름을 남긴다
	// 판매자 조회 때 상품 서비스를 부르지 않으려고 저장한다 (정산은 대회 다음 날 만들어져 그 뒤 이름이 바뀔 일은 드물다)
	@Column(nullable = false, length = 200)
	private String raceName;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutStage payoutStage;

	// YYYY-MM. 1차는 환불 마감일이 속한 달, 최종은 대회일이 속한 달 (실행일 · 정산 생성일이 아니다)
	@Column(nullable = false, length = 7)
	private String payoutMonth;

	@Column(nullable = false)
	private long salesAmount;

	@Column(nullable = false)
	private long refundAmount;

	@Column(nullable = false)
	private long feeAmount;

	// 1차 정산금 차감: 최종에서 빼는 선금 잔고 (1차 정산금 − 회수 요청액, 손에 남은 1차 정산금). 1차는 0이다 (payout.md 4-4)
	// 만들 때의 값이고, 같은 금액의 선지급_차감 원장 줄이 함께 생긴다
	@Column(nullable = false)
	private long advanceDeductionAmount;

	// 지급액: 이 정산 내역 원장 줄의 합 (캐시). 상계 줄이 더해지면 줄고, 취소하면 0이 된다
	@Column(nullable = false)
	private long payoutAmount;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private PayoutStatus status;

	@Column(nullable = false)
	private LocalDateTime settledAt;

	// 지급 예정일. 지급 배치는 이 날이 지난 지급_예정만 지급한다
	// 보류를 풀 때(PRO-26) 다시 정하므로(1차 · 최종 모두 max(원래 지급 예정일, 해제일 + 1일))
	// 배치는 보류 기록을 보지 않아도 된다
	@Column(nullable = false)
	private LocalDate scheduledPayDate;

	private LocalDateTime paidAt;

	// 지급 중에 대회가 취소된 시각 (PRO-253). 지급 중에는 돈이 이미 나갔을 수 있어 바로 닫지 못하므로 표시만 남긴다
	// 지급 결과가 나오면 성공은 지급 완료로 바꾼 뒤 회수를 만들고, 거절은 정산 예정으로 되돌리지 않고 취소로 닫는다
	private LocalDateTime raceCanceledAt;

	// 지급 결과를 모른(타임아웃 · 5xx) 날의 수 (BUK-166). MAX_UNKNOWN_PAY번이 되면 다시 요청하지 않고 지급_확인_필요로 멈춘다
	// 거절(4xx) · 불일치(409)는 세지 않는다. 돈이 나갔는지 아는 결과다
	// 같은 기준일에는 한 번만 센다. 관리자가 같은 날 지급 배치를 여러 번 돌려도 몇 분 만에 멈추지 않게 한다 (PR #427 리뷰)
	@Column(nullable = false)
	private int unknownPayCount;

	// 마지막으로 결과 모름을 센 기준일. 같은 날 다시 모르면 세지 않는다
	private LocalDate lastUnknownPayDate;

	// 낙관적 락: 지급 배치와 보류 API가 같은 정산 내역을 동시에 바꾸면 늦게 커밋한 쪽이 실패한다
	// 없으면 배치가 읽은 지급_예정을 근거로, 그 사이 걸린 지급보류를 지급_완료로 덮어쓸 수 있다
	@Version
	private long version;

	// 정산 항목은 추가만 한다. orphanRemoval을 켜면 리스트에서 빼는 것만으로 행이 지워지므로 쓰지 않는다
	@OneToMany(mappedBy = "payout", cascade = ALL)
	private List<PayoutItem> items = new ArrayList<>();

	// 상태 이력도 추가만 한다. 상태를 바꾸는 메서드가 changeStatus로 한 줄씩 남겨 빠뜨릴 수 없게 한다 (PRO-34)
	// LAZY라 상태를 바꿀 때 이력 목록을 한 번 읽는다. 그래서 상태 변경은 트랜잭션(세션) 안에서만 한다
	@OneToMany(mappedBy = "payout", cascade = ALL)
	private List<PayoutStatusHistory> statusHistories = new ArrayList<>();

	// 지급보류는 사유가 다르면 여러 개 걸 수 있다 (BUK-67). 마지막 보류가 풀릴 때만 정산 예정으로 돌리려고 들고 있는다
	// 저장은 PayoutHoldUseCase가 저장소로 하므로 cascade를 걸지 않는다
	@OneToMany(mappedBy = "payout")
	private List<PayoutHold> holds = new ArrayList<>();
	private Payout(long sellerId, long raceId, String raceName, PayoutStage payoutStage, YearMonth payoutMonth,
		LocalDateTime settledAt, LocalDate scheduledPayDate) {
		this.sellerId = sellerId;
		this.raceId = raceId;
		this.raceName = raceName;
		this.payoutStage = payoutStage;
		this.payoutMonth = payoutMonth.toString();
		this.settledAt = settledAt;
		this.scheduledPayDate = scheduledPayDate;
		// 정산 내역은 배치가 만든다
		changeStatus(PayoutStatus.지급_예정, PayoutHold.SYSTEM_MEMBER_ID, settledAt, null);
	}

	// 1차 정산 내역: 정산 월은 환불 마감일의 달이고, 지급 예정일은 정산 월의 다음 달 20일이다 (예: 마감 10/11 → 11/20)
	public static Payout first(long sellerId, long raceId, String raceName, LocalDate refundDeadline,
		LocalDateTime settledAt, BusinessDayCalendar calendar) {
		YearMonth payoutMonth = YearMonth.from(refundDeadline);
		return new Payout(sellerId, raceId, raceName, PayoutStage.일차_정산, payoutMonth, settledAt,
			payDayOf(payoutMonth.plusMonths(1), calendar));
	}

	// 최종 정산 내역: 정산 월은 대회일의 달이고, 지급 예정일은 정산 월의 다음 달 20일이다 (예: 대회 10/17 → 11/20)
	// 마감일과 대회일이 같은 달이면 1차와 같은 날 지급된다. 그날은 지급 배치가 1차 → 최종 순서로 지급한다 (PRO-188)
	// 1차 정산금 차감은 만든 뒤 deductAdvance로 쓴다
	public static Payout finalStage(long sellerId, long raceId, String raceName, LocalDate raceDate,
		LocalDateTime settledAt, BusinessDayCalendar calendar) {
		YearMonth payoutMonth = YearMonth.from(raceDate);
		return new Payout(sellerId, raceId, raceName, PayoutStage.최종_정산, payoutMonth, settledAt,
			payDayOf(payoutMonth.plusMonths(1), calendar));
	}

	// 바깥에서 remove, clear로 원장 줄을 바꾸지 못하도록 읽기 전용으로 내보낸다 (추가는 Payout의 메서드로만)
	public List<PayoutItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	public List<PayoutStatusHistory> getStatusHistories() {
		return Collections.unmodifiableList(statusHistories);
	}

	/**
	 * 1차 정산금 줄을 코스마다 한 줄씩 쓴다. 참가권 목록 없이 판매 집계(가격 합 · 장수)만으로 계산한다 (PRO-214)
	 * 코스 하나는 참가권 가격이 하나다. 그래서 가격 합 ÷ 장수가 1장 가격이고,
	 * 1차 정산금 = DOWN(1장 가격 × 40%) × 장수는 장마다 40%를 버려 더한 값(정책 Q7)과 같다. 회수 몫(1장 × 40%)과도 정확히 맞는다
	 * 가격 합이 장수로 나누어떨어지지 않으면 가격이 섞인 것이므로 계산하지 않고 실패로 남긴다
	 */
	public PayoutItem recordAdvance(long raceCourseId, long priceSum, int ticketCount, LocalDate payoutCreateDate) {
		if (payoutStage != PayoutStage.일차_정산) {
			throw new IllegalStateException("선금은 1차 정산에만 씁니다. payoutStage=%s".formatted(payoutStage));
		}
		if (ticketCount <= 0 || priceSum % ticketCount != 0) {
			throw new IllegalStateException(
				"한 코스의 참가권 가격이 하나가 아닙니다. raceId=%d, raceCourseId=%d, priceSum=%d, ticketCount=%d"
					.formatted(raceId, raceCourseId, priceSum, ticketCount));
		}
		boolean alreadyRecorded = items.stream()
			.anyMatch(item -> item.getRecordType() == PayoutRecordType.선금
				&& Long.valueOf(raceCourseId).equals(item.getRaceCourseId()));
		if (alreadyRecorded) {
			throw new IllegalStateException(
				"선금 줄은 코스마다 하나만 씁니다. raceId=%d, raceCourseId=%d".formatted(raceId, raceCourseId));
		}
		salesAmount += priceSum;
		long share = TwoStagePayoutCalculator.firstShare(priceSum / ticketCount) * ticketCount;
		return addLine(PayoutRecordType.선금, null, raceCourseId, share, null, payoutCreateDate);
	}

	// 최종 정산에 참가권 1장을 넣는다. 원장 줄은 정산 내역(Payout)을 통해서만 만든다 (금액 캐시와 함께 바뀌어야 하므로)
	// 환불 마감일까지 환불된 참가권은 판매 내역에서 빠지므로 판매만 기록한다 (payout.md 2-3)
	// 참가권마다 판매(+가격)와 수수료(−가격 × 5.5%, 원 미만 버림) 두 줄을 쓰고, 코스별로 볼 수 있게 코스를 적는다
	// 지급액은 언제나 이 정산 내역 원장 줄의 합이다
	public PayoutItem addItem(long orderItemId, Long raceCourseId, long price, BigDecimal feeRate,
		LocalDate payoutCreateDate) {
		if (payoutStage != PayoutStage.최종_정산) {
			throw new IllegalStateException("1차 정산은 참가권 줄 없이 recordAdvance로 만듭니다. raceId=%d".formatted(raceId));
		}
		salesAmount += price;
		long fee = TwoStagePayoutCalculator.rate(price, feeRate);
		feeAmount += fee;
		PayoutItem sale = addLine(PayoutRecordType.판매, orderItemId, raceCourseId, price, feeRate, payoutCreateDate);
		addLine(PayoutRecordType.수수료, orderItemId, raceCourseId, -fee, feeRate, payoutCreateDate);
		return sale;
	}

	// 코스를 모를 때(테스트 · 샘플 데이터)
	public PayoutItem addItem(long orderItemId, long price, BigDecimal feeRate, LocalDate payoutCreateDate) {
		return addItem(orderItemId, null, price, feeRate, payoutCreateDate);
	}

	/**
	 * 최종은 그때 남은 1차 정산금(선금 잔고)을 한 줄로 뺀다. 0원이어도 남겨 "뺄 1차 정산금이 없었다"를 기록한다 (PRO-214)
	 * 1차 정산금 차감은 만들 때의 선금 잔고(1차 정산금 − 회수 요청액)다. 최종 정산 내역마다 한 번만 쓴다
	 *
	 * @param amount 남은 1차 정산금 (선금 잔고)
	 */
	public void deductAdvance(long amount) {
		if (payoutStage != PayoutStage.최종_정산) {
			throw new IllegalStateException("1차 정산금 차감은 최종 정산에만 씁니다. payoutStage=%s".formatted(payoutStage));
		}
		boolean alreadyDeducted = items.stream()
			.anyMatch(item -> item.getRecordType() == PayoutRecordType.선지급_차감);
		if (alreadyDeducted) {
			throw new IllegalStateException("1차 정산금 차감 줄은 하나만 씁니다. raceId=%d".formatted(raceId));
		}
		this.advanceDeductionAmount = amount;
		addLine(PayoutRecordType.선지급_차감, null, null, -amount, null, null);
	}

	/**
	 * 지급 전 정산 내역에서 판매자가 갚을 돈을 뺀다 (회수 상계, PRO-214)
	 * 정산 내역 금액까지만 뺀다. 정산 내역은 0 아래로 내려가지 않고, 남은 갚을 돈은 갚을_돈에 남아 입금으로 받는다
	 *
	 * @param receivable 같은 판매자 × 대회의 갚을 돈(갚을_돈 잔고)
	 * @return 이번에 뺀 금액. 뺄 게 없으면 0이고 줄을 쓰지 않는다
	 */
	public long offsetReceivable(long receivable) {
		if (!status.isBeforePayment()) {
			throw new IllegalStateException("송금 전 정산 내역에서만 상계할 수 있습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		long offset = Math.min(receivable, payoutAmount);
		if (offset <= 0) {
			return 0;
		}
		addLine(PayoutRecordType.회수_상계, null, null, -offset, null, null);
		return offset;
	}

	private PayoutItem addLine(PayoutRecordType recordType, Long orderItemId, Long raceCourseId, long amount,
		BigDecimal feeRate, LocalDate payoutCreateDate) {
		PayoutItem item = PayoutItem.ofPayout(this, recordType, orderItemId, raceCourseId, amount, feeRate,
			payoutCreateDate);
		items.add(item);
		payoutAmount += amount;
		return item;
	}

	// 저장해서 ID를 받은 뒤 부른다. 하루 65,536건을 넘지 않는 한 같은 날 같은 코드가 나오지 않는다
	public void assignPayoutCode() {
		this.payoutCode = "ST-%s-%04X".formatted(settledAt.format(CODE_DATE), getId() & 0xFFFF);
	}

	/**
	 * 캐시 서비스에 정산금 이동을 요청하기 전에 지급 중으로 바꾼다 (PRO-252)
	 * 이 상태를 먼저 커밋해 두면, 요청 결과를 모르는 동안에도 보류 · 상계 · 취소가 이 정산 내역을 바꾸지 못한다
	 * 배치 두 개가 같은 정산 내역을 동시에 고르면 낙관적 락(@Version) 때문에 늦게 커밋한 쪽이 실패해 한쪽만 요청한다
	 */
	public void startPaying(LocalDateTime startedAt) {
		if (status != PayoutStatus.지급_예정) {
			throw new IllegalStateException("정산 예정 상태가 아니라 지급을 시작할 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		changeStatus(PayoutStatus.지급_중, PayoutHold.SYSTEM_MEMBER_ID, startedAt, null);
	}

	/**
	 * 캐시 서비스가 요청을 거절해(4xx) 돈이 옮겨지지 않은 것이 확실할 때 정산 예정으로 되돌린다 (PRO-252)
	 * 지급 예정일은 그대로 두므로 다음 실행에서 다시 지급한다. 결과를 모를 때(타임아웃 · 5xx)는 되돌리지 않고 지급_중으로 둔다
	 */
	public void revertPaying(LocalDateTime revertedAt, String reason) {
		if (status != PayoutStatus.지급_중) {
			throw new IllegalStateException("지급 중 상태가 아니라 되돌릴 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		changeStatus(PayoutStatus.지급_예정, PayoutHold.SYSTEM_MEMBER_ID, revertedAt, reason);
	}

	/**
	 * 지급 결과를 몰랐다고 센다 (BUK-166). 같은 기준일에는 한 번만 센다. MAX_UNKNOWN_PAY번째면 지급_확인_필요로 바꿔 지급 배치가 더는 고르지 않게 한다
	 * 그 전에는 지급_중으로 두고 다음 실행에서 같은 금액으로 다시 요청한다
	 *
	 * @return 이번에 지급_확인_필요로 바뀌었으면 true
	 */
	public boolean recordUnknownPay(LocalDate runDate, LocalDateTime recordedAt) {
		if (status != PayoutStatus.지급_중) {
			throw new IllegalStateException("지급 중 상태가 아니라 결과 모름을 셀 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		if (runDate.equals(lastUnknownPayDate)) {
			return false;
		}
		lastUnknownPayDate = runDate;
		unknownPayCount++;
		if (unknownPayCount < MAX_UNKNOWN_PAY) {
			return false;
		}
		changeStatus(PayoutStatus.지급_확인_필요, PayoutHold.SYSTEM_MEMBER_ID, recordedAt,
			"지급 결과를 %d번 모름".formatted(unknownPayCount));
		return true;
	}

	// 지급 불일치: 캐시 서비스가 같은 정산 ID를 이미 다른 판매자 · 금액으로 처리했다고 답했다(409, BUK-62)
	// 돈은 캐시 서비스에 저장된 금액으로 나갔다. 정산 예정으로 되돌리면 매일 다시 요청하므로 따로 멈춘다
	public void markPayMismatch(LocalDateTime markedAt, String reason) {
		if (status != PayoutStatus.지급_중) {
			throw new IllegalStateException("지급 중 상태가 아니라 지급 불일치로 바꿀 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		changeStatus(PayoutStatus.지급_불일치, PayoutHold.SYSTEM_MEMBER_ID, markedAt, reason);
	}

	// 지급 완료: 캐시 서비스가 정산금을 판매자 · 시스템 지갑으로 옮긴 뒤 바꾼다 (PRO-252)
	// 옮길 돈이 0원인 정산 내역은 캐시 서비스를 부르지 않으므로 정산 예정에서 바로 바꾼다
	// 보류된 정산 내역은 지급 완료로 바꿀 수 없다. 배치가 지급보류를 고르지 않지만, 잘못 불려도 여기서 막는다
	public void pay(LocalDateTime paidAt) {
		if (status != PayoutStatus.지급_예정 && status != PayoutStatus.지급_중) {
			throw new IllegalStateException("정산 예정 · 지급 중 상태가 아니라 지급할 수 없습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		// 지급은 지급 배치가 한다
		changeStatus(PayoutStatus.지급_완료, PayoutHold.SYSTEM_MEMBER_ID, paidAt, null);
		this.paidAt = paidAt;
	}

	// 캐시 서비스로 옮길 돈이 있는지. 지급액(대금)이 상계로 0원이어도 수수료가 남아 있으면 옮긴다 (PRO-252)
	public boolean hasTransferAmount() {
		return payoutAmount > 0 || feeAmount > 0;
	}

	// 지급보류 걸기: 지급 예정 · 지급보류일 때 건다. 사유가 다르면 여러 개 걸 수 있다 (BUK-67 H1)
	// 같은 사유가 이미 걸려 있으면 막는다. 보류 기록(PayoutHold)은 여기서만 만든다. 저장은 호출한 쪽이 한다
	public PayoutHold hold(PayoutHoldReason reasonType, String reason, long heldBy, LocalDateTime heldAt) {
		// 캐시 서비스에 돈을 옮겨 달라고 요청한 뒤라 돈이 이미 나갔을 수 있다 (PRO-252)
		if (status == PayoutStatus.지급_중) {
			throw new PayoutHoldConflictException("지급 중인 정산 내역입니다.");
		}
		if (status == PayoutStatus.지급_완료) {
			throw new PayoutHoldConflictException("이미 지급 완료된 정산 내역입니다.");
		}
		// 돈은 이미 나갔다. 보류했다가 풀면 정산 예정으로 돌아가 다시 지급 요청을 하게 된다 (BUK-62)
		if (status == PayoutStatus.지급_불일치) {
			throw new PayoutHoldConflictException("지급 금액이 맞지 않아 확인 중인 정산 내역입니다.");
		}
		// 돈이 나갔는지 모른다. 지급 중과 같다 (BUK-166)
		if (status == PayoutStatus.지급_확인_필요) {
			throw new PayoutHoldConflictException("지급 결과를 확인 중인 정산 내역입니다.");
		}
		if (status == PayoutStatus.취소) {
			throw new PayoutHoldConflictException("취소된 정산 내역입니다.");
		}
		if (isHeldFor(reasonType)) {
			throw new PayoutHoldConflictException("이미 같은 사유로 지급보류 중인 정산 내역입니다.");
		}
		// 이미 보류 중이면 상태는 그대로다. 더 건 사유는 보류 기록에 남는다
		if (status != PayoutStatus.지급보류) {
			changeStatus(PayoutStatus.지급보류, heldBy, heldAt, reasonType.name().replace('_', ' '));
		}
		PayoutHold hold = new PayoutHold(this, reasonType, reason, heldBy, heldAt);
		holds.add(hold);
		return hold;
	}

	// 이 사유의 보류가 걸려 있는지 (같은 사유는 두 번 걸 수 없다)
	public boolean isHeldFor(PayoutHoldReason reasonType) {
		return activeHolds().anyMatch(active -> active.getReasonType() == reasonType);
	}

	// 이 사유로 걸려 있는 보류의 ID(HD-). 없으면 빈 값 (BUK-108 이상 감지)
	public Optional<String> activeHoldCode(PayoutHoldReason reasonType) {
		return activeHolds().filter(active -> active.getReasonType() == reasonType).map(PayoutHold::getHoldCode)
			.findFirst();
	}

	private Stream<PayoutHold> activeHolds() {
		return holds.stream().filter(hold -> hold.getStatus() == PayoutHoldStatus.지급보류);
	}

	// 지급보류 풀기: 정산 예정으로 돌리고 지급 예정일을 max(원래 지급 예정일, 해제일 + 1일)로 다시 정한다
	// (10/28 결정, 10/29 지급일을 20일로 되돌릴 때도 유지, 11/2 다시 확인. 휴일 보정은 없다: 주말에도 지급)
	// 원래 지급 예정일보다 앞당기지 않는다 (PRO-128). 보류를 걸 때는 지급 예정일을 바꾸지 않으므로
	// 지금 값이 원래 지급 예정일(또는 이전 해제로 이미 늦춰진 날)이다. 해제일은 갈수록 늦어지므로
	// 지금 값과 비교해도 원래 지급 예정일과 비교한 것과 같다. 그래서 원래 지급 예정일을 따로 저장하지 않는다
	// calendar는 지금 쓰지 않는다. 해제 뒤 지급일에도 영업일 보정을 켤 때 호출부를 다시 고치지 않도록 인자로 남겨 둔다
	// 지급 배치는 상태와 지급 예정일만 보므로, 보류 기록을 보지 않아도 다음 지급일에 지급된다
	public void release(PayoutHold hold, long releasedBy, LocalDateTime releasedAt, BusinessDayCalendar calendar) {
		// ==로 비교하지 않는다. LAZY로 읽은 hold.getPayout()은 Hibernate 프록시라 같은 행이어도 this와 다른 객체다
		// 프록시의 getId()는 DB를 읽지 않고 들고 있는 ID를 돌려준다
		if (hold.getPayout().getId() != getId()) {
			throw new IllegalArgumentException("이 정산 내역의 지급보류가 아닙니다. payoutId=%d".formatted(getId()));
		}
		// 대회 취소로 닫힌 정산 내역에는 보류 기록이 지급보류로 남아 있을 수 있다. 풀어도 지급 예정으로 돌리지 않는다
		if (status == PayoutStatus.취소) {
			throw new PayoutHoldConflictException("취소된 정산 내역의 지급보류는 풀 수 없습니다.");
		}
		hold.markReleased(releasedBy, releasedAt);
		// 다른 사유의 보류가 남아 있으면 지급보류 그대로 둔다. 지급 예정일은 마지막 보류를 풀 때 정한다 (BUK-67 H2)
		if (activeHolds().findAny().isPresent()) {
			return;
		}
		changeStatus(PayoutStatus.지급_예정, releasedBy, releasedAt, null);
		LocalDate payDateAfterRelease = releasedAt.toLocalDate().plusDays(PAY_DAYS_AFTER_RELEASE);
		if (payDateAfterRelease.isAfter(scheduledPayDate)) {
			this.scheduledPayDate = payDateAfterRelease;
		}
	}

	// 대회 취소로 닫기: 지급 전(지급_예정 · 지급보류) 정산 내역을 취소로 바꿔 다시는 지급되지 않게 한다 (PRO-34)
	// 원장 줄은 지우지 않고 반대 부호 줄을 더해 되돌린다(PRO-214). 그래서 조건 없이 더해도 1차 정산금 · 갚을 돈 잔고가 맞는다
	// 지급액(줄의 합)은 0이 되고, 판매액 · 수수료 칸은 만들 때 그대로 둔다(얼마가 나갈 뻔했는지 남긴다)
	// 보류 중이었으면 보류 기록은 지급보류로 두고 상태만 최신으로 바꾼다. 무엇이었는지는 상태 이력에 남는다
	// 이미 지급한 정산 내역은 닫지 않는다. 지급 뒤 대회 취소는 회수(PRO-33)가 맡는다
	// 지급 중에도 돈이 이미 나갔을 수 있어 닫지 않는다. 대신 markRaceCanceled로 표시하고 지급 결과를 기다린다 (PRO-253)
	public void cancel(long canceledBy, LocalDateTime canceledAt, String reason) {
		if (!status.isBeforePayment()) {
			throw new IllegalStateException("지급 전 정산 내역만 취소할 수 있습니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		close(canceledBy, canceledAt, reason);
	}

	/**
	 * 지급 중에 대회가 취소됐다고 표시한다 (PRO-253). 같은 대회 취소 메시지를 다시 받아도 처음 시각을 그대로 둔다
	 *
	 * @return 이번에 처음 표시했으면 true
	 */
	public boolean markRaceCanceled(LocalDateTime canceledAt) {
		if (!status.isAwaitingPayResult()) {
			throw new IllegalStateException("지급 결과를 기다리는 정산 내역만 대회 취소를 표시합니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		if (raceCanceledAt != null) {
			return false;
		}
		this.raceCanceledAt = canceledAt;
		return true;
	}

	public boolean isRaceCanceled() {
		return raceCanceledAt != null;
	}

	// 대회 취소를 표시한 지급 중 정산 내역의 지급이 거절됐다. 돈이 나가지 않았으므로 정산 예정으로 되돌리지 않고 취소로 닫는다
	// (되돌리면 다음 지급 배치가 취소된 대회의 정산금을 내보낸다, PRO-253)
	public void cancelRejected(LocalDateTime canceledAt, String reason) {
		if (status != PayoutStatus.지급_중 || raceCanceledAt == null) {
			throw new IllegalStateException("대회 취소를 표시한 지급 중 정산 내역만 거절 뒤 취소합니다. payoutId=%d, status=%s"
				.formatted(getId(), status));
		}
		close(PayoutHold.SYSTEM_MEMBER_ID, canceledAt, reason);
	}

	// 원장 줄을 반대 부호로 되돌리고 취소로 닫는다
	private void close(long canceledBy, LocalDateTime canceledAt, String reason) {
		List<PayoutItem> original = List.copyOf(items);
		original.forEach(item -> addLine(item.getRecordType().canceled(), item.getOrderItemId(),
			item.getRaceCourseId(), -item.getAmount(), item.getFeeRate(), item.getPayoutCreateDate()));
		changeStatus(PayoutStatus.취소, canceledBy, canceledAt, reason);
	}

	private void changeStatus(PayoutStatus to, long changedBy, LocalDateTime changedAt, String reason) {
		statusHistories.add(new PayoutStatusHistory(this, status, to, changedBy, changedAt, reason));
		this.status = to;
	}

	// 그 달의 지급일: 20일. 주말이어도 그날이다 (11/2 결정). dev 샘플 데이터(PayoutDataInit)도 이 날짜로 시나리오를 맞춘다
	// calendar는 지금 쓰지 않는다. 휴일 보정을 다시 켤 때 호출부를 다시 고치지 않도록 인자로 남겨 둔다 (10/28과 같은 방식)
	public static LocalDate payDayOf(YearMonth month, BusinessDayCalendar calendar) {
		return month.atDay(PAY_DAY_OF_MONTH);
	}
}
