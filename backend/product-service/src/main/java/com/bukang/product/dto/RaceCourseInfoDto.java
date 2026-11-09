package com.bukang.product.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 서비스에 주는 종목 정보 (내부 API). 주문 서비스 RaceCourseInfo에 있는 필드는 이름·뜻이 같다.
 * 나머지 필드(대회명·종목명, 판매 상태·남은 자리 등)는 주문 서비스가 아직 쓰지 않지만 함께 준다.
 * 키트 옵션은 여기 넣지 않는다. 주문도 공개 API(GET /api/v1/product/races/{raceId}/kit-options)로 받는다 (BUK-120)
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
	// 크루(파티) 예매 기간 (race.crew_sale_start_at·crew_sale_end_at). 크루 모집이 없는 대회는 null
	// 주문이 파티 신청·합류 때 파티 예매 기간인지, 크루 가입이 시작 7일 전까지였는지 본다
	private final LocalDateTime crewSaleStartAt;
	private final LocalDateTime crewSaleEndAt;
	// 파티 인원 최소·최대 (race.crew_min_count·crew_max_count). 최대가 null이면 상한이 없다
	private final int crewMinCount;
	private final Integer crewMaxCount;

	public static RaceCourseInfoDto from(RaceCourse course) {
		Race race = course.getRace();
		return new RaceCourseInfoDto(course.getId(), race.getId(), race.getTitle(), race.getRaceDate(),
			race.getIndividualOpenAt(), race.getApplicationCloseAt(), course.getMinAge(), course.getMaxAge(),
			race.getSeller().getId(), course.getName(), race.getTitle() + " " + course.getName(), course.getPrice(),
			course.getKitPrice(), race.getRefundDeadline(), race.getScheduleChangedAt(), race.getSaleStatus(),
			course.getStatus(), course.individualRemaining(), course.crewRemaining(), course.isKitIncluded(),
			race.getCrewSaleStartAt(), race.getCrewSaleEndAt(), race.getCrewMinCount(),
			race.getCrewMaxCount());
	}
}
