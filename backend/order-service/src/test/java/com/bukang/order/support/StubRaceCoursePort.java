package com.bukang.order.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

/**
 * 테스트용 종목 정보. 테스트마다 접수 기간·나이 제한을 원하는 대로 넣는다
 */
public class StubRaceCoursePort implements RaceCoursePort {
	public static final long SELLER_ID = 7;
	public static final long PRICE = 50_000;
	// 키트가 있는 테스트 종목이 주는 티셔츠 사이즈
	public static final List<String> TSHIRT_SIZES = List.of("S", "M", "L", "XL");

	private final Map<Long, RaceCourseInfo> courses = new HashMap<>();
	// 상품 서비스가 멈춘 상황 (HTTP 어댑터는 연결 실패를 503 예외로 바꾼다)
	private volatile boolean unavailable;

	@Override
	public Optional<RaceCourseInfo> findCourse(long raceCourseId) {
		if (unavailable) {
			throw new ExternalServiceUnavailableException("상품 서비스에 연결하지 못했습니다.");
		}
		return Optional.ofNullable(courses.get(raceCourseId));
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void put(RaceCourseInfo course) {
		courses.put(course.raceCourseId(), course);
	}

	public void clear() {
		courses.clear();
		unavailable = false;
	}

	// 지금 접수 중이고 대회는 60일 뒤, 환불 기한은 40일 뒤인 종목
	public static RaceCourseInfo openCourse(long raceCourseId, long raceId, int minAge, Integer maxAge) {
		return openCourse(raceCourseId, raceId, minAge, maxAge, LocalDate.now().plusDays(40));
	}

	// 환불 기한을 정한 접수 중 종목 (취소 테스트에서 기한 경계를 맞출 때 쓴다)
	public static RaceCourseInfo openCourse(long raceCourseId, long raceId, int minAge, Integer maxAge,
		LocalDate refundDeadline) {
		return openCourse(raceCourseId, raceId, minAge, maxAge, refundDeadline, null);
	}

	// 환불 기한과 일정 변경 일시를 정한 접수 중 종목 (일정 변경 뒤 7일 전액 환불 테스트에서 쓴다)
	public static RaceCourseInfo openCourse(long raceCourseId, long raceId, int minAge, Integer maxAge,
		LocalDate refundDeadline, LocalDateTime scheduleChangedAt) {
		LocalDateTime now = LocalDateTime.now();
		return new RaceCourseInfo(raceCourseId, raceId, now.plusDays(60), now.minusDays(1), now.plusDays(30),
			minAge, maxAge, SELLER_ID, "테스트 대회 종목 " + raceCourseId, PRICE, true, refundDeadline,
			scheduleChangedAt, false, TSHIRT_SIZES);
	}

	// 키트는 있지만 티셔츠는 없는 접수 중 종목 (사이즈를 받지 않는다)
	public static RaceCourseInfo courseWithoutTshirt(long raceCourseId, long raceId) {
		RaceCourseInfo course = openCourse(raceCourseId, raceId, 14, null);
		return new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(), course.applicationOpenAt(),
			course.applicationCloseAt(), course.minAge(), course.maxAge(), course.sellerId(), course.courseName(),
			course.price(), course.kitIncluded(), course.refundDeadline(), course.scheduleChangedAt(), false);
	}

	// 키트가 없는 접수 중 종목 (배송 주소를 받지 않는다)
	public static RaceCourseInfo courseWithoutKit(long raceCourseId, long raceId) {
		LocalDateTime now = LocalDateTime.now();
		return new RaceCourseInfo(raceCourseId, raceId, now.plusDays(60), now.minusDays(1), now.plusDays(30),
			14, null, SELLER_ID, "테스트 대회 종목 " + raceCourseId, PRICE, false, now.toLocalDate().plusDays(40),
			null);
	}

	// 접수가 이미 마감된 종목
	public static RaceCourseInfo closedCourse(long raceCourseId, long raceId) {
		LocalDateTime now = LocalDateTime.now();
		return new RaceCourseInfo(raceCourseId, raceId, now.plusDays(10), now.minusDays(30), now.minusDays(1),
			14, null, SELLER_ID, "테스트 대회 종목 " + raceCourseId, PRICE, true, now.toLocalDate().plusDays(5),
			null, false, TSHIRT_SIZES);
	}

	// 같은 종목을 판매자가 취소한 대회로 바꾼다 (B saleStatus = CANCELED)
	public static RaceCourseInfo canceled(RaceCourseInfo course) {
		return new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(), course.applicationOpenAt(),
			course.applicationCloseAt(), course.minAge(), course.maxAge(), course.sellerId(), course.courseName(),
			course.price(), course.kitIncluded(), course.refundDeadline(), course.scheduleChangedAt(), true,
			course.tshirtSizeOption());
	}

	// 파티 예매 정보를 붙인다 (파티 신청·합류 최대 인원 테스트)
	public static RaceCourseInfo withCrewSale(RaceCourseInfo course, LocalDateTime startAt, LocalDateTime endAt,
		int minCount, Integer maxCount) {
		return new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(), course.applicationOpenAt(),
			course.applicationCloseAt(), course.minAge(), course.maxAge(), course.sellerId(), course.courseName(),
			course.price(), course.kitIncluded(), course.refundDeadline(), course.scheduleChangedAt(),
			course.canceled(), course.tshirtSizeOption(),
			new RaceCourseInfo.CrewSale(startAt, endAt, minCount, maxCount));
	}
}
