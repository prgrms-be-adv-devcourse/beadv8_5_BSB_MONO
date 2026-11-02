package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.TshirtSize;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 서비스에 주는 종목 정보 (내부 API). 주문 서비스 RaceCourseInfo에 있는 필드는 이름·뜻이 같다.
 * 나머지 필드(대회명·종목명, 판매 상태·남은 자리·티셔츠 사이즈 등)는 주문 서비스가 아직 쓰지 않지만 함께 준다.
 */
@AllArgsConstructor
@Getter
public class RaceCourseInfoDto {
	private final long raceCourseId;
	private final long raceId;
	// 대회명 (race.title)
	private final String title;
	private final LocalDateTime raceDate;
	// 개인 예매 오픈 일시 (race.individual_open_at)
	private final LocalDateTime applicationOpenAt;
	private final LocalDateTime applicationCloseAt;
	private final int minAge;
	private final Integer maxAge;
	private final long sellerId;
	// 종목명 (race_course.name)
	private final String course;
	// 주문 상품명으로 남길 이름 ("대회명 종목명")
	private final String courseName;
	// 구매자가 내는 참가권 한 장 값. 키트 금액은 이 안에 들어 있다
	private final long price;
	// 참가비에 포함된 키트 금액 (따로 더하지 않는다)
	private final long kitPrice;
	private final LocalDate refundDeadline;
	// 판매 시작 뒤 대회 일시·장소를 바꾼 일시. 이날부터 7일 동안 전액 환불
	private final LocalDateTime scheduleChangedAt;
	private final RaceSaleStatus saleStatus;
	private final CourseStatus courseStatus;
	private final int individualRemaining;
	private final int crewRemaining;
	private final boolean kitIncluded;
	private final List<TshirtSize> tshirtSizeOption;

	public static RaceCourseInfoDto from(RaceCourse course) {
		Race race = course.getRace();
		return new RaceCourseInfoDto(course.getId(), race.getId(), race.getTitle(), race.getRaceDate(),
			race.getIndividualOpenAt(), race.getApplicationCloseAt(), course.getMinAge(), course.getMaxAge(),
			race.getSeller().getId(), course.getName(), race.getTitle() + " " + course.getName(), course.getPrice(),
			course.getKitPrice(), race.getRefundDeadline(), race.getScheduleChangedAt(), race.getSaleStatus(),
			course.getStatus(), course.individualRemaining(), course.crewRemaining(), course.isKitIncluded(),
			course.getTshirtSizeOption());
	}
}
