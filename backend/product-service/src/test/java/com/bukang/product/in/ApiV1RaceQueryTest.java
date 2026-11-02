package com.bukang.product.in;

import static com.bukang.product.support.JsonIds.id;
import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.domain.TshirtSize;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

/**
 * 대회 조회 API(⑧-2)를 확인한다.
 * - 대회 상세(GET /api/v1/product/races/{raceId}): 대회·판매자·종목(남은 자리·티셔츠 사이즈) 필드,
 *   판매 예정 대회는 등록하자마자 누구나 보고, 취소된 대회는 등록한 판매자만 보고 다른 사람에게는 없는 대회(404)로 보이는지,
 *   없는 대회 404, 형식 오류 400
 * - 내 대회(GET /api/v1/product/races/mine): 최근 등록 순, 종목 정원·결제 대기·판매 수가 자리 확보·판매·복구로
 *   바뀌는지, 승인된 판매자가 아니면 403, 헤더 없음 401
 * - 주문용 종목 정보(GET /internal/v1/product/courses/{raceCourseId}): 주문 서비스 RaceCourseInfo 13필드
 *   (applicationOpenAt = 개인 예매 오픈, courseName = "대회명 종목명")와 추가 필드, 판매 예정 대회의 종목도 200,
 *   취소된 대회의 종목은 200(saleStatus CANCELED)
 */
class ApiV1RaceQueryTest extends ProductIntegrationTest {
	private static final String RACES = "/api/v1/product/races/";
	private static final String MINE = "/api/v1/product/races/mine";
	private static final String COURSES = "/internal/v1/product/courses/";
	private static final String RACE_NOT_FOUND = "존재하지 않는 대회입니다.";
	private static final String COURSE_NOT_FOUND = "존재하지 않는 종목입니다.";
	private static final String SELLER_NOT_APPROVED = "승인된 판매자만 대회를 등록·수정할 수 있습니다.";
	private static final String INVALID_VALUE_TYPE = "요청 값의 형식이 올바르지 않습니다.";
	// 자동 증가 ID가 닿지 않는 없는 대회·종목 ID
	private static final long MISSING_ID = Integer.MAX_VALUE;
	// 기본으로 들어가는 두 판매자 말고 이 테스트에서 따로 넣는 판매자
	private static final long PENDING_SELLER_MEMBER_ID = 9003;
	private static final long PENDING_SELLER_ID = 7003;
	private static final long INDIVIDUAL_SELLER_MEMBER_ID = 9004;
	private static final long INDIVIDUAL_SELLER_ID = 7004;

	// ---------- 대회 상세 ----------

	@Test
	@DisplayName("판매 중 대회 상세는 로그인 없이 200이고, 대회 일정·판매자(7001 (주)러닝7001)·종목 2개(10K, 하프)를 종목 ID 순으로 준다")
	void findOnSaleRaceDetail() throws Exception {
		RaceBuilder builder = race().crewCount(5, 20);
		LocalDateTime now = builder.now();
		LocalDate refundDeadline = now.plusDays(15).toLocalDate();
		RaceSaveRequestDto request = builder
			.raceDate(now.plusDays(20))
			.individualOpenAt(now.minusHours(2))
			.applicationCloseAt(now.plusDays(18))
			.refundDeadline(refundDeadline)
			.course(
				course(),
				course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180).price(60_000)
					.kit(5_000, TshirtSize.XL, TshirtSize.S, TshirtSize.M).age(19, 70).quota(0, 50))
			.build();
		RegisteredRace registered = registerOnSale(request);
		long tenKmId = registered.courseIds().get(0);
		long halfId = registered.courseIds().get(1);

		ResultActions result = getAs(RACES + registered.raceId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("대회를 조회했습니다."))
			.andExpect(jsonPath("$.data.raceId").value(registered.raceId()))
			.andExpect(jsonPath("$.data.title").value("2026 서울 하프 마라톤"))
			.andExpect(jsonPath("$.data.description").value("서울숲에서 달리는 가을 러닝 대회"))
			.andExpect(jsonPath("$.data.location").value("서울숲 일대"))
			.andExpect(jsonPath("$.data.regionSido").value("서울특별시"))
			.andExpect(jsonPath("$.data.regionSigungu").value("성동구"))
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
			.andExpect(jsonPath("$.data.crewSaleStartAt", nullValue()))
			.andExpect(jsonPath("$.data.crewSaleEndAt", nullValue()))
			.andExpect(jsonPath("$.data.refundDeadline").value(refundDeadline.toString()))
			.andExpect(jsonPath("$.data.refundPolicy").value("환불 기한까지 취소하면 전액 환불됩니다."))
			.andExpect(jsonPath("$.data.crewMinCount").value(5))
			.andExpect(jsonPath("$.data.crewMaxCount").value(20))
			.andExpect(jsonPath("$.data.scheduleChangedAt", nullValue()))
			// 판매자 정보
			.andExpect(jsonPath("$.data.seller.sellerId").value(SELLER_ID))
			.andExpect(jsonPath("$.data.seller.sellerType").value("BUSINESS"))
			.andExpect(jsonPath("$.data.seller.businessName").value("(주)러닝" + SELLER_ID))
			.andExpect(jsonPath("$.data.seller.businessNumber").value("123-45-67890"))
			.andExpect(jsonPath("$.data.seller.representativeName").value("김대표"))
			// 키트 없는 10K: 티셔츠 사이즈는 빈 목록
			.andExpect(jsonPath("$.data.courses", hasSize(2)))
			.andExpect(jsonPath("$.data.courses[0].raceCourseId").value(tenKmId))
			.andExpect(jsonPath("$.data.courses[0].name").value("10K"))
			.andExpect(jsonPath("$.data.courses[0].kitIncluded").value(false))
			.andExpect(jsonPath("$.data.courses[0].kitPrice").value(0))
			.andExpect(jsonPath("$.data.courses[0].tshirtSizeOption", hasSize(0)))
			.andExpect(jsonPath("$.data.courses[0].maxAge", nullValue()))
			.andExpect(jsonPath("$.data.courses[0].individualQuota").value(100))
			.andExpect(jsonPath("$.data.courses[0].individualRemaining").value(100))
			// 키트 있는 하프: 티셔츠 사이즈는 보낸 순서(XL, S, M)와 상관없이 작은 사이즈부터
			.andExpect(jsonPath("$.data.courses[1].raceCourseId").value(halfId))
			.andExpect(jsonPath("$.data.courses[1].name").value("하프"))
			.andExpect(jsonPath("$.data.courses[1].courseType").value("HALF"))
			.andExpect(jsonPath("$.data.courses[1].surface").value("ROAD"))
			.andExpect(jsonPath("$.data.courses[1].distanceKm").value(21.1))
			.andExpect(jsonPath("$.data.courses[1].timeLimitMinutes").value(180))
			.andExpect(jsonPath("$.data.courses[1].minAge").value(19))
			.andExpect(jsonPath("$.data.courses[1].maxAge").value(70))
			.andExpect(jsonPath("$.data.courses[1].price").value(60_000))
			.andExpect(jsonPath("$.data.courses[1].kitIncluded").value(true))
			.andExpect(jsonPath("$.data.courses[1].kitPrice").value(5_000))
			.andExpect(jsonPath("$.data.courses[1].tshirtSizeOption", contains("S", "M", "XL")))
			.andExpect(jsonPath("$.data.courses[1].status").value("OPEN"))
			.andExpect(jsonPath("$.data.courses[1].crewQuota").value(0))
			.andExpect(jsonPath("$.data.courses[1].individualQuota").value(50))
			.andExpect(jsonPath("$.data.courses[1].crewRemaining").value(0))
			.andExpect(jsonPath("$.data.courses[1].individualRemaining").value(50));

		String body = bodyOf(result);
		assertThat(dateTimeAt(body, "$.data.raceDate")).isEqualTo(now.plusDays(20));
		// 크루 예매가 없으면 개인 예매 오픈 날짜가 판매 시작일이다
		assertThat(LocalDate.parse(JsonPath.<String>read(body, "$.data.saleOpenDate")))
			.isEqualTo(now.minusHours(2).toLocalDate());
		assertThat(dateTimeAt(body, "$.data.individualOpenAt")).isEqualTo(now.minusHours(2));
		assertThat(dateTimeAt(body, "$.data.applicationCloseAt")).isEqualTo(now.plusDays(18));
	}

	@Test
	@DisplayName("비사업자 판매자(회원 9004 → 판매자 7004, 성명 이러너)의 대회 상세는 판매자 구분 INDIVIDUAL이고 "
		+ "상호명·사업자등록번호는 null이다")
	void findIndividualSellerRaceDetail() throws Exception {
		ProductMember member = saveMember(INDIVIDUAL_SELLER_MEMBER_ID);
		LocalDateTime now = LocalDateTime.now();
		productSellerRepository.save(new ProductSeller(INDIVIDUAL_SELLER_ID, member, SellerType.INDIVIDUAL, null, null,
			"이러너", SellerStatus.APPROVED, now, now));
		RaceBuilder builder = race();
		RegisteredRace registered = register(INDIVIDUAL_SELLER_MEMBER_ID,
			builder.individualOpenAt(builder.now().plusDays(2)).build());

		getAs(RACES + registered.raceId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.seller.sellerId").value(INDIVIDUAL_SELLER_ID))
			.andExpect(jsonPath("$.data.seller.sellerType").value("INDIVIDUAL"))
			.andExpect(jsonPath("$.data.seller.businessName", nullValue()))
			.andExpect(jsonPath("$.data.seller.businessNumber", nullValue()))
			.andExpect(jsonPath("$.data.seller.representativeName").value("이러너"));
	}

	@Test
	@DisplayName("개인 정원 3석 종목에서 2석 확보·1석 판매 뒤 상세의 남은 자리는 1석, 3석째 확보 뒤 0석·정원 마감, "
		+ "판매 1석 복구 뒤 다시 1석·접수 중이다")
	void findDetailReflectsRemainingSeats() throws Exception {
		RegisteredRace registered = registerOnSale(race().course(course().quota(0, 3)).build());
		long courseId = registered.courseId();
		String path = RACES + registered.raceId();
		holdSeat(courseId, 1001);
		holdSeat(courseId, 1002);
		sellSeat(1001);

		expectDetailRemaining(path, 1, "OPEN");

		holdSeat(courseId, 1003);

		expectDetailRemaining(path, 0, "SOLD_OUT");

		restoreSeat(1001);

		expectDetailRemaining(path, 1, "OPEN");
	}

	@Test
	@DisplayName("개인 예매가 이틀 뒤 열리는 대회는 등록하자마자 판매 예정이고, 헤더 없음·등록한 판매자(9001)·다른 판매자(9002)·"
		+ "판매자 아닌 회원(9999) 모두 200으로 본다")
	void scheduledRaceVisibleToEveryone() throws Exception {
		RaceBuilder builder = race();
		RegisteredRace registered = register(builder.individualOpenAt(builder.now().plusDays(2)).build());
		String path = RACES + registered.raceId();

		for (Long memberId : new Long[] {null, SELLER_MEMBER_ID, OTHER_SELLER_MEMBER_ID, NOT_SELLER_MEMBER_ID}) {
			getAs(path, memberId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.raceId").value(registered.raceId()))
				.andExpect(jsonPath("$.data.saleStatus").value("SCHEDULED"))
				.andExpect(jsonPath("$.data.courses[0].raceCourseId").value(registered.courseId()));
		}
	}

	@Test
	@DisplayName("판매 중이던 대회를 취소하면 등록한 판매자(9001)만 200(CANCELED)이고 헤더 없음·다른 판매자(9002)·"
		+ "판매자 아닌 회원(9999)은 404다")
	void canceledRaceVisibleOnlyToOwner() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());
		String path = RACES + registered.raceId();
		getAs(path, null).andExpect(status().isOk());

		cancelRace(registered.raceId());

		getAs(path, SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.raceId").value(registered.raceId()))
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"));
		expectRaceNotFound(getAs(path, null));
		expectRaceNotFound(getAs(path, OTHER_SELLER_MEMBER_ID));
		expectRaceNotFound(getAs(path, NOT_SELLER_MEMBER_ID));
	}

	@Test
	@DisplayName("없는 대회 ID(2147483647)로 상세를 조회하면 로그인 여부와 상관없이 404다")
	void findMissingRace() throws Exception {
		expectRaceNotFound(getAs(RACES + MISSING_ID, null));
		expectRaceNotFound(getAs(RACES + MISSING_ID, SELLER_MEMBER_ID));
	}

	@Test
	@DisplayName("대회 ID가 숫자가 아니거나(abc) X-Member-Id 헤더가 숫자가 아니면(abc) 상세 조회는 400이다")
	void findDetailWithInvalidValue() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());

		getAs(RACES + "abc", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(INVALID_VALUE_TYPE))
			.andExpect(jsonPath("$.data", nullValue()));
		// 있는 대회라 404가 아니라 헤더 형식 때문에 400이다
		mockMvc.perform(get(RACES + registered.raceId()).header("X-Member-Id", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	// ---------- 내 대회 ----------

	@Test
	@DisplayName("내 대회는 판매 예정·판매 중을 가리지 않고 최근 등록 순(세 번째·두 번째·첫 번째)으로 나오고 "
		+ "다른 판매자(9002)의 대회는 빠진다")
	void findMineInRecentOrder() throws Exception {
		RaceBuilder firstBuilder = race().title("첫 번째 대회");
		RegisteredRace first = register(firstBuilder.individualOpenAt(firstBuilder.now().plusDays(2)).build());
		RegisteredRace second = registerOnSale(race().title("두 번째 대회").build());
		RaceBuilder builder = race().title("세 번째 대회");
		LocalDateTime now = builder.now();
		RegisteredRace third = register(builder
			.raceDate(now.plusDays(20))
			.individualOpenAt(now.plusDays(2))
			.applicationCloseAt(now.plusDays(18))
			.course(course().name("하프").type(CourseType.HALF).distanceKm("21.1").price(60_000).quota(0, 70))
			.build());
		RaceBuilder othersBuilder = race().title("다른 판매자 대회");
		RegisteredRace others = register(OTHER_SELLER_MEMBER_ID,
			othersBuilder.individualOpenAt(othersBuilder.now().plusDays(2)).build());

		ResultActions result = getAs(MINE, SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("내 대회 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data", hasSize(3)))
			.andExpect(jsonPath("$.data[*].raceId",
				contains(id(third.raceId()), id(second.raceId()), id(first.raceId()))))
			.andExpect(jsonPath("$.data[*].title", contains("세 번째 대회", "두 번째 대회", "첫 번째 대회")))
			.andExpect(jsonPath("$.data[*].saleStatus", contains("SCHEDULED", "ON_SALE", "SCHEDULED")))
			.andExpect(jsonPath("$.data[0].courses", hasSize(1)))
			.andExpect(jsonPath("$.data[0].courses[0].raceCourseId").value(third.courseId()))
			.andExpect(jsonPath("$.data[0].courses[0].name").value("하프"))
			.andExpect(jsonPath("$.data[0].courses[0].price").value(60_000))
			.andExpect(jsonPath("$.data[0].courses[0].status").value("OPEN"))
			.andExpect(jsonPath("$.data[0].courses[0].quota").value(70))
			.andExpect(jsonPath("$.data[0].courses[0].reserved").value(0))
			.andExpect(jsonPath("$.data[0].courses[0].sold").value(0));

		String body = bodyOf(result);
		assertThat(dateTimeAt(body, "$.data[0].raceDate")).isEqualTo(now.plusDays(20));
		// 크루 예매가 없으면 개인 예매 오픈 날짜가 판매 시작일이다
		assertThat(LocalDate.parse(JsonPath.<String>read(body, "$.data[0].saleOpenDate")))
			.isEqualTo(now.plusDays(2).toLocalDate());
		assertThat(dateTimeAt(body, "$.data[0].applicationCloseAt")).isEqualTo(now.plusDays(18));

		getAs(MINE, OTHER_SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].raceId", contains(id(others.raceId()))));
	}

	@Test
	@DisplayName("내 대회 종목 정원은 크루 10 + 개인 5 = 15이고, 3석 확보·1석 판매 뒤 결제 대기 2·판매 1, "
		+ "판매 1석 복구 뒤 결제 대기 2·판매 0이다")
	void findMineReflectsReservedAndSold() throws Exception {
		RaceBuilder builder = race();
		LocalDateTime now = builder.now();
		// 크루 예매(3일 전~2일 전)가 끝났고 개인 예매가 크루 마감 다음 날 06:00에 열려, 지금 개인 자리를 확보할 수 있다
		RaceSaveRequestDto request = builder
			.crewSale(now.minusDays(3), now.minusDays(2))
			.individualOpenAt(now.minusDays(1).toLocalDate().atTime(6, 0))
			.course(course().quota(10, 5))
			.build();
		RegisteredRace registered = registerOnSale(request);
		long courseId = registered.courseId();

		expectMyCourseCounts(15, 0, 0);

		holdSeat(courseId, 2001);
		holdSeat(courseId, 2002);
		holdSeat(courseId, 2003);
		sellSeat(2001);

		expectMyCourseCounts(15, 2, 1);

		restoreSeat(2001);

		expectMyCourseCounts(15, 2, 0);
	}

	@Test
	@DisplayName("판매자가 아닌 회원(9999)과 승인 대기(PENDING) 판매자인 회원(9003)이 내 대회를 조회하면 403이다")
	void findMineByNotApprovedSeller() throws Exception {
		ProductMember member = saveMember(PENDING_SELLER_MEMBER_ID);
		productSellerRepository.save(new ProductSeller(PENDING_SELLER_ID, member, SellerType.BUSINESS, "(주)대기러닝",
			"111-22-33333", "박대표", SellerStatus.PENDING, LocalDateTime.now(), null));

		expectSellerNotApproved(getAs(MINE, NOT_SELLER_MEMBER_ID));
		expectSellerNotApproved(getAs(MINE, PENDING_SELLER_MEMBER_ID));
	}

	@Test
	@DisplayName("내 대회를 X-Member-Id 헤더 없이 조회하면 401, 헤더가 숫자가 아니면(abc) 400이다")
	void findMineWithoutValidHeader() throws Exception {
		getAs(MINE, null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
			.andExpect(jsonPath("$.data", nullValue()));
		mockMvc.perform(get(MINE).header("X-Member-Id", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(MemberIdHeader.INVALID_MESSAGE));
	}

	// ---------- 주문용 종목 정보 ----------

	@Test
	@DisplayName("판매 중 대회의 종목 정보는 주문 서비스 13필드와 추가 필드(대회명·종목명·판매 상태·남은 자리·키트·티셔츠 사이즈)를 "
		+ "주고, applicationOpenAt은 개인 예매 오픈, courseName은 '2026 춘천 가을 마라톤 하프'다")
	void findCourseInfo() throws Exception {
		RaceBuilder builder = race().title("2026 춘천 가을 마라톤");
		LocalDateTime now = builder.now();
		LocalDateTime crewSaleStartAt = now.minusDays(3);
		// 크루 마감 다음 날 06:00. 크루 예매 시작과 달라야 applicationOpenAt이 개인 예매 오픈인지 알 수 있다
		LocalDateTime individualOpenAt = now.minusDays(1).toLocalDate().atTime(6, 0);
		LocalDate refundDeadline = now.plusDays(15).toLocalDate();
		RaceSaveRequestDto request = builder
			.crewSale(crewSaleStartAt, now.minusDays(2))
			.individualOpenAt(individualOpenAt)
			.raceDate(now.plusDays(20))
			.applicationCloseAt(now.plusDays(18))
			.refundDeadline(refundDeadline)
			.course(course().name("하프").type(CourseType.HALF).distanceKm("21.1").price(60_000)
				.kit(5_000, TshirtSize.L, TshirtSize.S, TshirtSize.M).age(19, 70).quota(10, 90))
			.build();
		RegisteredRace registered = registerOnSale(request);
		holdSeat(registered.courseId(), 4001);

		ResultActions result = getAs(COURSES + registered.courseId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("종목 정보를 조회했습니다."))
			.andExpect(jsonPath("$.data.raceCourseId").value(registered.courseId()))
			.andExpect(jsonPath("$.data.raceId").value(registered.raceId()))
			.andExpect(jsonPath("$.data.minAge").value(19))
			.andExpect(jsonPath("$.data.maxAge").value(70))
			.andExpect(jsonPath("$.data.sellerId").value(SELLER_ID))
			.andExpect(jsonPath("$.data.courseName").value("2026 춘천 가을 마라톤 하프"))
			// price가 참가권 한 장 값이고 kitPrice는 그 안에 포함된 키트 금액이다 (더하지 않는다)
			.andExpect(jsonPath("$.data.price").value(60_000))
			.andExpect(jsonPath("$.data.kitPrice").value(5_000))
			.andExpect(jsonPath("$.data.refundDeadline").value(refundDeadline.toString()))
			.andExpect(jsonPath("$.data.scheduleChangedAt", nullValue()))
			// 아래는 주문 서비스 RaceCourseInfo에 없는 추가 필드
			.andExpect(jsonPath("$.data.title").value("2026 춘천 가을 마라톤"))
			.andExpect(jsonPath("$.data.course").value("하프"))
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
			.andExpect(jsonPath("$.data.courseStatus").value("OPEN"))
			.andExpect(jsonPath("$.data.individualRemaining").value(89))
			.andExpect(jsonPath("$.data.crewRemaining").value(10))
			.andExpect(jsonPath("$.data.kitIncluded").value(true))
			.andExpect(jsonPath("$.data.tshirtSizeOption", contains("S", "M", "L")));

		String body = bodyOf(result);
		Map<String, Object> data = JsonPath.read(body, "$.data");
		assertThat(data).containsOnlyKeys("raceCourseId", "raceId", "title", "raceDate", "applicationOpenAt",
			"applicationCloseAt", "minAge", "maxAge", "sellerId", "course", "courseName", "price", "kitPrice",
			"refundDeadline", "scheduleChangedAt", "saleStatus", "courseStatus", "individualRemaining", "crewRemaining",
			"kitIncluded", "tshirtSizeOption");
		assertThat(dateTimeAt(body, "$.data.raceDate")).isEqualTo(now.plusDays(20));
		assertThat(dateTimeAt(body, "$.data.applicationOpenAt"))
			.isEqualTo(individualOpenAt)
			.isNotEqualTo(crewSaleStartAt);
		assertThat(dateTimeAt(body, "$.data.applicationCloseAt")).isEqualTo(now.plusDays(18));
	}

	@Test
	@DisplayName("판매 중 대회의 대회 일시를 15일 뒤에서 16일 뒤로 미루면 종목 정보·상세의 일정 변경 일시(scheduleChangedAt)가 "
		+ "수정한 시각으로 채워진다")
	void findCourseInfoAfterRaceDateChanged() throws Exception {
		RaceBuilder builder = race();
		LocalDateTime now = builder.now();
		RegisteredRace registered = registerOnSale(builder.raceDate(now.plusDays(15)).build());

		LocalDateTime before = LocalDateTime.now();
		// 같은 요청에서 대회 일시만 바꾼다 (환불 기준일은 대회 날짜를 따라 늦춰진다)
		putJson(RACES + registered.raceId(), SELLER_MEMBER_ID,
			builder.raceDate(now.plusDays(16)).course(course().courseId(registered.courseId())).build())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회를 수정했습니다."));
		LocalDateTime after = LocalDateTime.now();

		String body = bodyOf(getAs(COURSES + registered.courseId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.refundDeadline").value(now.plusDays(16).toLocalDate().toString())));
		assertThat(dateTimeAt(body, "$.data.raceDate")).isEqualTo(now.plusDays(16));
		assertThat(dateTimeAt(body, "$.data.scheduleChangedAt"))
			.isBetween(before.minusMinutes(1), after.plusMinutes(1));
		getAs(RACES + registered.raceId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.scheduleChangedAt").isNotEmpty());
	}

	@Test
	@DisplayName("판매 예정(개인 예매 이틀 뒤 오픈) 대회의 종목 정보도 200이며 "
		+ "courseName은 '2026 서울 하프 마라톤 10K', 키트 없는 종목이라 kitPrice 0·티셔츠 사이즈 없음이다")
	void findScheduledCourseInfo() throws Exception {
		RaceBuilder builder = race();
		LocalDateTime individualOpenAt = builder.now().plusDays(2);
		RegisteredRace registered = register(builder.individualOpenAt(individualOpenAt).build());

		ResultActions result = getAs(COURSES + registered.courseId(), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.raceCourseId").value(registered.courseId()))
			.andExpect(jsonPath("$.data.saleStatus").value("SCHEDULED"))
			.andExpect(jsonPath("$.data.courseName").value("2026 서울 하프 마라톤 10K"))
			.andExpect(jsonPath("$.data.price").value(45_000))
			.andExpect(jsonPath("$.data.minAge").value(14))
			.andExpect(jsonPath("$.data.maxAge", nullValue()))
			.andExpect(jsonPath("$.data.kitIncluded").value(false))
			.andExpect(jsonPath("$.data.kitPrice").value(0))
			.andExpect(jsonPath("$.data.tshirtSizeOption", hasSize(0)))
			.andExpect(jsonPath("$.data.courseStatus").value("OPEN"))
			.andExpect(jsonPath("$.data.individualRemaining").value(100))
			.andExpect(jsonPath("$.data.crewRemaining").value(0));
		assertThat(dateTimeAt(bodyOf(result), "$.data.applicationOpenAt")).isEqualTo(individualOpenAt);
	}

	@Test
	@DisplayName("취소된 대회의 종목 정보는 200(saleStatus CANCELED)이고, 결제 대기 1석이 해제되어 남은 자리가 99석에서 100석이 된다")
	void findCanceledCourseInfo() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());
		String path = COURSES + registered.courseId();
		holdSeat(registered.courseId(), 3001);
		getAs(path, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.individualRemaining").value(99));

		cancelRace(registered.raceId());

		getAs(path, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.raceCourseId").value(registered.courseId()))
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"))
			.andExpect(jsonPath("$.data.courseStatus").value("OPEN"))
			.andExpect(jsonPath("$.data.individualRemaining").value(100));
	}

	@Test
	@DisplayName("없는 종목 ID(2147483647)의 종목 정보는 404, 숫자가 아닌 종목 ID(abc)는 400이다")
	void findMissingOrInvalidCourseInfo() throws Exception {
		expectCourseNotFound(getAs(COURSES + MISSING_ID, null));
		getAs(COURSES + "abc", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(INVALID_VALUE_TYPE))
			.andExpect(jsonPath("$.data", nullValue()));
	}

	// ---------- 도우미 ----------

	// 기본 판매자 말고 다른 판매자를 넣을 때 쓰는 회원 복제본
	private ProductMember saveMember(long memberId) {
		LocalDateTime now = LocalDateTime.now();
		return productMemberRepository.save(new ProductMember(memberId, now, now, "러너" + memberId));
	}

	private void holdSeat(long raceCourseId, long participationId) throws Exception {
		hold(raceCourseId, participationId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("자리를 확보했습니다."));
	}

	private void sellSeat(long participationId) throws Exception {
		sell(participationId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("자리를 판매했습니다."));
	}

	private void restoreSeat(long participationId) throws Exception {
		restore(participationId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("자리를 정원으로 되돌렸습니다."));
	}

	private void cancelRace(long raceId) throws Exception {
		postAs(RACES + raceId + "/cancel", SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회를 취소했습니다."));
	}

	// 대회 상세의 첫 종목 남은 개인 자리와 종목 상태
	private void expectDetailRemaining(String path, int individualRemaining, String courseStatus) throws Exception {
		getAs(path, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.courses[0].individualRemaining").value(individualRemaining))
			.andExpect(jsonPath("$.data.courses[0].status").value(courseStatus));
	}

	// 판매자(9001)의 내 대회 첫 줄 첫 종목의 정원·결제 대기·판매 수
	private void expectMyCourseCounts(int quota, int reserved, int sold) throws Exception {
		getAs(MINE, SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].courses[0].quota").value(quota))
			.andExpect(jsonPath("$.data[0].courses[0].reserved").value(reserved))
			.andExpect(jsonPath("$.data[0].courses[0].sold").value(sold));
	}

	private static void expectRaceNotFound(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.message").value(RACE_NOT_FOUND))
			.andExpect(jsonPath("$.data", nullValue()));
	}

	private static void expectCourseNotFound(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.message").value(COURSE_NOT_FOUND))
			.andExpect(jsonPath("$.data", nullValue()));
	}

	private static void expectSellerNotApproved(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.message").value(SELLER_NOT_APPROVED))
			.andExpect(jsonPath("$.data", nullValue()));
	}

	private static String bodyOf(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// 일시는 초가 0일 때 형식이 달라질 수 있어 문자열이 아니라 LocalDateTime으로 바꿔 비교한다
	private static LocalDateTime dateTimeAt(String body, String path) {
		String value = JsonPath.read(body, path);
		return LocalDateTime.parse(value);
	}
}
