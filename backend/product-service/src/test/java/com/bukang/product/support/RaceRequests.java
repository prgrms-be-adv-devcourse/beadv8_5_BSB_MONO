package com.bukang.product.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.TshirtSize;
import com.bukang.product.dto.RaceCourseSaveRequestDto;
import com.bukang.product.dto.RaceImageRequestDto;
import com.bukang.product.dto.RaceSaveRequestDto;

/**
 * 대회 등록·수정 요청을 만든다. 기본값은 "한 시간 전에 개인 예매가 열린 서울 대회, 10K 종목 1개(개인 정원 100, 대표 이미지 1장)"다.
 * 테스트마다 바꿀 값만 고친다. 예: race().title("부산 바다 런").course(course().price(30000)).build()
 */
public final class RaceRequests {
	// 요청 안에서 같은 파일을 두 번 넣으면 400이므로, 대표 이미지 fileId는 만들 때마다 다르게 준다
	private static final AtomicLong FILE_ID = new AtomicLong(1);

	private RaceRequests() {
	}

	public static RaceBuilder race() {
		return new RaceBuilder();
	}

	public static CourseBuilder course() {
		return new CourseBuilder();
	}

	public static RaceImageRequestDto thumbnail() {
		return new RaceImageRequestDto(FILE_ID.getAndIncrement(), FileType.THUMBNAIL, 0);
	}

	public static RaceImageRequestDto image(FileType fileType, int sortNo) {
		return new RaceImageRequestDto(FILE_ID.getAndIncrement(), fileType, sortNo);
	}

	public static final class RaceBuilder {
		private final LocalDateTime now = LocalDateTime.now().withNano(0);
		private String title = "2026 서울 하프 마라톤";
		private String description = "서울숲에서 달리는 가을 러닝 대회";
		private String location = "서울숲 일대";
		private String regionSido = "서울특별시";
		private String regionSigungu = "성동구";
		private LocalDateTime raceDate = now.plusDays(15);
		private LocalDateTime crewSaleStartAt;
		private LocalDateTime crewSaleEndAt;
		// 기본은 한 시간 전에 열려 자리를 확보할 수 있다. 판매 시작일은 오늘로부터 이틀 뒤부터만 등록할 수 있어
		// registerOnSale()로 만들고, register()로 등록하려면 이틀 뒤 이후로 바꾼다
		// (예: individualOpenAt(now().plusDays(2)) → 판매 예정)
		private LocalDateTime individualOpenAt = now.minusHours(1);
		private LocalDateTime applicationCloseAt = now.plusDays(10);
		private LocalDate refundDeadline;
		private String refundPolicy = "환불 기한까지 취소하면 전액 환불됩니다.";
		private Integer crewMinCount = 5;
		private Integer crewMaxCount;
		private final List<RaceCourseSaveRequestDto> courses = new ArrayList<>();

		public RaceBuilder title(String title) {
			this.title = title;
			return this;
		}

		public RaceBuilder description(String description) {
			this.description = description;
			return this;
		}

		public RaceBuilder location(String location) {
			this.location = location;
			return this;
		}

		public RaceBuilder region(String regionSido, String regionSigungu) {
			this.regionSido = regionSido;
			this.regionSigungu = regionSigungu;
			return this;
		}

		public RaceBuilder raceDate(LocalDateTime raceDate) {
			this.raceDate = raceDate;
			return this;
		}

		public RaceBuilder crewSale(LocalDateTime startAt, LocalDateTime endAt) {
			this.crewSaleStartAt = startAt;
			this.crewSaleEndAt = endAt;
			return this;
		}

		public RaceBuilder individualOpenAt(LocalDateTime individualOpenAt) {
			this.individualOpenAt = individualOpenAt;
			return this;
		}

		public RaceBuilder applicationCloseAt(LocalDateTime applicationCloseAt) {
			this.applicationCloseAt = applicationCloseAt;
			return this;
		}

		public RaceBuilder refundDeadline(LocalDate refundDeadline) {
			this.refundDeadline = refundDeadline;
			return this;
		}

		public RaceBuilder crewCount(Integer minCount, Integer maxCount) {
			this.crewMinCount = minCount;
			this.crewMaxCount = maxCount;
			return this;
		}

		// 부르지 않으면 기본 종목(course()) 1개를 넣는다
		public RaceBuilder course(CourseBuilder... builders) {
			for (CourseBuilder builder : builders) {
				courses.add(builder.build());
			}
			return this;
		}

		public LocalDateTime now() {
			return now;
		}

		public RaceSaveRequestDto build() {
			List<RaceCourseSaveRequestDto> requestCourses = new ArrayList<>(courses);
			if (requestCourses.isEmpty()) {
				requestCourses.add(RaceRequests.course().build());
			}
			LocalDate deadline = refundDeadline != null ? refundDeadline : raceDate.toLocalDate();
			return new RaceSaveRequestDto(title, description, location, regionSido, regionSigungu, raceDate,
				crewSaleStartAt, crewSaleEndAt, individualOpenAt, applicationCloseAt, deadline, refundPolicy,
				crewMinCount, crewMaxCount, requestCourses);
		}
	}

	public static final class CourseBuilder {
		private Long courseId;
		private String name = "10K";
		private CourseType courseType = CourseType.KM_10;
		private CourseSurface surface = CourseSurface.ROAD;
		private BigDecimal distanceKm = new BigDecimal("10");
		private Integer timeLimitMinutes = 90;
		private Long price = 45_000L;
		private Boolean kitIncluded = false;
		private Long kitPrice = 0L;
		private List<TshirtSize> tshirtSizeOption;
		private Integer minAge = 14;
		private Integer maxAge;
		private Integer crewQuota = 0;
		private Integer individualQuota = 100;
		private List<RaceImageRequestDto> images;

		// 수정 요청에서 기존 종목을 가리킬 때
		public CourseBuilder courseId(Long courseId) {
			this.courseId = courseId;
			return this;
		}

		public CourseBuilder name(String name) {
			this.name = name;
			return this;
		}

		public CourseBuilder type(CourseType courseType) {
			this.courseType = courseType;
			return this;
		}

		public CourseBuilder surface(CourseSurface surface) {
			this.surface = surface;
			return this;
		}

		public CourseBuilder distanceKm(String distanceKm) {
			this.distanceKm = new BigDecimal(distanceKm);
			return this;
		}

		public CourseBuilder timeLimitMinutes(Integer timeLimitMinutes) {
			this.timeLimitMinutes = timeLimitMinutes;
			return this;
		}

		public CourseBuilder price(long price) {
			this.price = price;
			return this;
		}

		// 키트 포함 + 티셔츠 사이즈 (비우면 티셔츠 없음)
		public CourseBuilder kit(long kitPrice, TshirtSize... sizes) {
			this.kitIncluded = true;
			this.kitPrice = kitPrice;
			this.tshirtSizeOption = List.of(sizes);
			return this;
		}

		public CourseBuilder tshirtSizes(List<TshirtSize> sizes) {
			this.tshirtSizeOption = sizes;
			return this;
		}

		public CourseBuilder age(Integer minAge, Integer maxAge) {
			this.minAge = minAge;
			this.maxAge = maxAge;
			return this;
		}

		public CourseBuilder quota(Integer crewQuota, Integer individualQuota) {
			this.crewQuota = crewQuota;
			this.individualQuota = individualQuota;
			return this;
		}

		// 부르지 않으면 대표 이미지 1장을 넣는다. 빈 목록이면 이미지 없이 보낸다
		public CourseBuilder images(RaceImageRequestDto... images) {
			this.images = List.of(images);
			return this;
		}

		public RaceCourseSaveRequestDto build() {
			List<RaceImageRequestDto> requestImages = images != null ? images : List.of(thumbnail());
			return new RaceCourseSaveRequestDto(courseId, name, courseType, surface, distanceKm, timeLimitMinutes,
				price, kitIncluded, kitPrice, tshirtSizeOption, minAge, maxAge, crewQuota, individualQuota,
				requestImages);
		}
	}
}
