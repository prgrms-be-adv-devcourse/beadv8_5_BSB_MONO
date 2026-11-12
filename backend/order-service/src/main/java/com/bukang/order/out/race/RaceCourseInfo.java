package com.bukang.order.out.race;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.Objects;

import com.bukang.common.shared.order.event.OrderItemRefundReason;

/**
 * 참가 신청에 필요한 종목(참가권) 정보. B(상품)의 race · race_course에서 필요한 값만 옮긴 것이다
 * (ERD 추천안 v4 RACE_RACE · RACE_COURSE)
 *
 * @param raceCourseId       종목 ID
 * @param raceId             대회 ID
 * @param raceDate           대회 일시. 나이는 이날의 만 나이로 따진다 (정책 2장 1-9)
 * @param applicationOpenAt  개인 예매 오픈 일시 (individual_open_at)
 * @param applicationCloseAt 접수 마감 일시 = 참가자 정보 수정 기한 (정책 2장 1-6 · 3장 1-5)
 * @param minAge             참가 가능 최소 나이 (만 14세 이상)
 * @param maxAge             참가 가능 최대 나이. null이면 제한 없음
 * @param sellerId           판매자 ID (정산이 주문 상품에서 읽는다)
 * @param courseName         주문 상품명으로 남길 이름 (예: "2026 서울 하프 마라톤 10K")
 * @param price              참가비 (원). 키트가 있는 종목은 키트를 포함한 값이다. 키트 금액은 따로 없다
 *                           (ERD v5 race_course: kit_included만 두고 "package_price 없음")
 * @param kitIncluded        키트가 있는 종목인지 (ERD v5 race_course.kit_included). 키트는 배송으로만 받으므로
 *                           있으면 신청 때 배송 주소를 받는다 (정책 2장 1-3, 3장 1-2)
 * @param refundDeadline     환불 기한 (날짜). 이날 23:59:59까지 취소하면 전액 환불한다 (정책 5장, ERD v4 race.refund_deadline)
 * @param scheduleChangedAt  일정 변경 안내 일시. 판매 시작 뒤 대회 일시가 바뀌면 상품 서비스가 기록한다. 바뀐 적 없으면 null
 * @param canceled           판매자가 취소한 대회인지 (B race.sale_status = CANCELED). 신청·담기·[결제하기]는 막고,
 *                           결제한 참가권은 환불 기한과 상관없이 전액 환불한다 (정책 5장 1-2 "대회 취소·일정 변경일 때만 환불")
 *                           (ERD v4 race.schedule_changed_at)
 * @param tshirtSizeOption   이 종목이 주는 기념 티셔츠 사이즈 (B race_course.tshirt_size_option, XS~XXXL 중 판매자가 고름).
 *                           비어 있으면 티셔츠가 없다. 판매 시작 뒤에는 B가 바꾸지 못하게 막는다
 * @param crewSale           파티(크루) 예매 정보. 상품이 아직 주지 않으면 null
 * @param raceName           대회명 (B race.title). 문의 서비스용 주문 요약에 쓴다 (BUK-160). 상품이 주지 않으면 null
 * @param course             종목명만 (B race_course.name, 예: "10K"). courseName은 "대회명 종목명"이다. 주지 않으면 null
 */
public record RaceCourseInfo(
	long raceCourseId,
	long raceId,
	LocalDateTime raceDate,
	LocalDateTime applicationOpenAt,
	LocalDateTime applicationCloseAt,
	int minAge,
	Integer maxAge,
	long sellerId,
	String courseName,
	long price,
	boolean kitIncluded,
	LocalDate refundDeadline,
	LocalDateTime scheduleChangedAt,
	boolean canceled,
	List<String> tshirtSizeOption,
	CrewSale crewSale,
	String raceName,
	String course
) {
	/**
	 * 파티(크루) 예매 정보 (B race.crew_sale_start_at·crew_sale_end_at·crew_min_count·crew_max_count). 파티 신청·합류에 쓴다
	 * shortcut: 상품 종목 조회에 아직 이 네 칸이 없어 HTTP 어댑터는 null을 준다(파티 신청은 503). 호빈님이 칸을 더하면 그대로 채워진다
	 *
	 * @param startAt  파티 예매 시작. 파티원은 이 시각 7일 전까지 크루에 가입했어야 한다 (11/05 회의 P4)
	 * @param endAt    파티 예매 마감 (크루 마감)
	 * @param minCount 파티 최소 인원 (판매자가 정함, 11/05 회의 P3: 4명 아래로는 못 내림)
	 * @param maxCount 파티 최대 인원 (판매자가 정함). null이면 제한 없음 (11/09 결정: 11/05 P3의 10명 상한을 없앰)
	 */
	public record CrewSale(LocalDateTime startAt, LocalDateTime endAt, int minCount, Integer maxCount) {
		// 이 인원이면 판매자 최대 인원을 넘는지. 최대가 없으면 넘지 않는다
		public boolean exceedsMax(int size) {
			return maxCount != null && size > maxCount;
		}

		// 파티 예매 시작 이상, 마감 전
		public boolean isOpen(LocalDateTime now) {
			return !now.isBefore(startAt) && now.isBefore(endAt);
		}

		// 크루 자리를 닫는 시각 = 결제 기한 상한. 마감이 정확히 0시면 그 시각, 아니면 마감일 다음 날 0시
		// 상품 Race.crewCloseAt(#357)과 같아야 한다. 상품 배치가 이 시각에 결제 대기 크루 자리를 모두 풀기 때문이다
		public LocalDateTime closeAt() {
			if (endAt.toLocalTime().equals(LocalTime.MIDNIGHT)) {
				return endAt;
			}
			return endAt.toLocalDate().plusDays(1).atStartOfDay();
		}

		// 이 시각까지 크루에 가입한 사람만 파티로 신청할 수 있다
		public LocalDateTime joinDeadline() {
			return startAt.minusDays(CREW_JOIN_DAYS_BEFORE_SALE);
		}
	}

	// 파티 예매 시작 며칠 전까지 크루에 가입해야 하는지 (11/05 회의 P4)
	public static final int CREW_JOIN_DAYS_BEFORE_SALE = 7;

	public RaceCourseInfo {
		// 상품이 빈 값을 섞어 보내도 종목 조회가 실패하지 않게 거른다
		tshirtSizeOption = tshirtSizeOption == null ? List.of()
			: tshirtSizeOption.stream().filter(Objects::nonNull).toList();
	}

	// 대회명·종목명을 따로 정하지 않을 때 (주문 요약 말고는 쓰지 않는다)
	public RaceCourseInfo(long raceCourseId, long raceId, LocalDateTime raceDate, LocalDateTime applicationOpenAt,
		LocalDateTime applicationCloseAt, int minAge, Integer maxAge, long sellerId, String courseName, long price,
		boolean kitIncluded, LocalDate refundDeadline, LocalDateTime scheduleChangedAt, boolean canceled,
		List<String> tshirtSizeOption, CrewSale crewSale) {
		this(raceCourseId, raceId, raceDate, applicationOpenAt, applicationCloseAt, minAge, maxAge, sellerId,
			courseName, price, kitIncluded, refundDeadline, scheduleChangedAt, canceled, tshirtSizeOption, crewSale,
			null, null);
	}

	// 파티 예매 정보를 따로 정하지 않을 때 (개인 신청만 쓰는 곳·테스트)
	public RaceCourseInfo(long raceCourseId, long raceId, LocalDateTime raceDate, LocalDateTime applicationOpenAt,
		LocalDateTime applicationCloseAt, int minAge, Integer maxAge, long sellerId, String courseName, long price,
		boolean kitIncluded, LocalDate refundDeadline, LocalDateTime scheduleChangedAt, boolean canceled,
		List<String> tshirtSizeOption) {
		this(raceCourseId, raceId, raceDate, applicationOpenAt, applicationCloseAt, minAge, maxAge, sellerId,
			courseName, price, kitIncluded, refundDeadline, scheduleChangedAt, canceled, tshirtSizeOption, null);
	}

	// 티셔츠 사이즈를 따로 정하지 않을 때 (티셔츠 없음)
	public RaceCourseInfo(long raceCourseId, long raceId, LocalDateTime raceDate, LocalDateTime applicationOpenAt,
		LocalDateTime applicationCloseAt, int minAge, Integer maxAge, long sellerId, String courseName, long price,
		boolean kitIncluded, LocalDate refundDeadline, LocalDateTime scheduleChangedAt, boolean canceled) {
		this(raceCourseId, raceId, raceDate, applicationOpenAt, applicationCloseAt, minAge, maxAge, sellerId,
			courseName, price, kitIncluded, refundDeadline, scheduleChangedAt, canceled, List.of());
	}

	// 취소되지 않은 대회 (가짜 어댑터·테스트처럼 취소 여부를 따로 정하지 않을 때)
	public RaceCourseInfo(long raceCourseId, long raceId, LocalDateTime raceDate, LocalDateTime applicationOpenAt,
		LocalDateTime applicationCloseAt, int minAge, Integer maxAge, long sellerId, String courseName, long price,
		boolean kitIncluded, LocalDate refundDeadline, LocalDateTime scheduleChangedAt) {
		this(raceCourseId, raceId, raceDate, applicationOpenAt, applicationCloseAt, minAge, maxAge, sellerId,
			courseName, price, kitIncluded, refundDeadline, scheduleChangedAt, false);
	}

	// 티셔츠를 주는 종목인지. 주면 참가 신청 때 사이즈를 이 목록에서 골라야 한다
	public boolean offersTshirt() {
		return !tshirtSizeOption.isEmpty();
	}

	// 일정이 바뀐 뒤 전액 환불해 주는 날 수 (정책 5장 4-4 "알린 날부터 7일 동안", ERD v4 race.schedule_changed_at)
	public static final int SCHEDULE_CHANGE_REFUND_DAYS = 7;

	/**
	 * 전액 환불로 취소할 수 있는 날인지 (ERD v4 환불 판정)
	 * 환불 기한 날짜까지(그날 23:59:59 포함, 10/12 결정)이거나, 일정이 바뀐 뒤 7일 안이면 기한과 상관없이 된다
	 */
	public boolean isRefundableOn(LocalDate today) {
		return canceled || !today.isAfter(refundDeadline) || isWithinScheduleChangeRefund(today);
	}

	// 오늘 취소하면 정산에 알릴 환불 사유 (BUK-74). isRefundableOn이 true일 때만 부른다
	// 환불 기한 안이면 일정이 바뀌었어도 구매자 취소로 본다 (정산은 두 사유를 똑같이 회수한다)
	public OrderItemRefundReason refundReasonOn(LocalDate today) {
		if (canceled) {
			return OrderItemRefundReason.RACE_CANCELED;
		}
		return today.isAfter(refundDeadline) ? OrderItemRefundReason.SCHEDULE_CHANGED
			: OrderItemRefundReason.BUYER_CANCELED;
	}

	/**
	 * 일정 변경 뒤 전액 환불 기간 안인지. 환불 기한과 같이 날짜 단위로 센다
	 * 정책 5장 4-4 "알린 날부터 7일 동안"대로 알린 날(일정 변경 일시)을 1일째로 센다: 10/13 14:00에 바뀌면 10/19 23:59:59까지다
	 */
	public boolean isWithinScheduleChangeRefund(LocalDate today) {
		LocalDate lastDay = scheduleChangeRefundLastDay();
		return lastDay != null && !today.isAfter(lastDay);
	}

	// 일정 변경 뒤 전액 환불 기간의 마지막 날 (그날 23:59:59까지). 일정이 바뀐 적 없으면 null
	public LocalDate scheduleChangeRefundLastDay() {
		if (scheduleChangedAt == null) {
			return null;
		}
		return scheduleChangedAt.toLocalDate().plusDays(SCHEDULE_CHANGE_REFUND_DAYS - 1);
	}

	// 접수 기간 안인지 (오픈 시각 이상, 마감 시각 전)
	public boolean isAccepting(LocalDateTime now) {
		return !canceled && !now.isBefore(applicationOpenAt) && now.isBefore(applicationCloseAt);
	}

	// 접수 마감 전인지 (참가자 정보 수정 가능 여부)
	public boolean isBeforeClose(LocalDateTime now) {
		return !canceled && now.isBefore(applicationCloseAt);
	}

	// 대회 당일 만 나이가 종목의 나이 제한 안인지
	public boolean allowsAge(LocalDate birth) {
		int age = ageOnRaceDay(birth);
		return age >= minAge && (maxAge == null || age <= maxAge);
	}

	public int ageOnRaceDay(LocalDate birth) {
		return Period.between(birth, raceDate.toLocalDate()).getYears();
	}

	// 사용자에게 보여 줄 나이 제한 문구 (예: "만 14세 이상", "만 20세 ~ 만 60세")
	public String ageRangeLabel() {
		if (maxAge == null) {
			return "만 %d세 이상".formatted(minAge);
		}
		return "만 %d세 ~ 만 %d세".formatted(minAge, maxAge);
	}
}
