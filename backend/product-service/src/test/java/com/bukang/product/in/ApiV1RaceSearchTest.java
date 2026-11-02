package com.bukang.product.in;

import static com.bukang.product.support.JsonIds.asLong;
import static com.bukang.product.support.JsonIds.id;
import static com.bukang.product.support.JsonIds.ids;
import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.JsonIds;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

/**
 * 공개 대회 목록·연관검색어 API(⑧-2)를 확인한다. 검색은 ES 컨테이너(nori)의 race_courses 문서로 한다.
 * <ul>
 *     <li>공개 대회 목록(GET /api/v1/product/races): 공개 조건(판매 예정·판매 중, 접수 마감 전), 종목 여러 개 대회도 한 줄,
 *     정렬 기본값과 5가지 정렬, 정확도 가중치(대회명 3배, 장소 2배, 소개 1배 순), 단어 하나만 맞아도 나오는 검색어,
 *     동의어("충남" → 충청남도), 오타 허용(단어 길이별), 필터(지역·종목 구분·노면·거리·참가비·대회 날짜),
 *     조건에 맞은 종목 기준 최저가·가격 낮은순, 대회 등록 일시 기준 최신 등록순, 페이지, 400 응답</li>
 *     <li>연관검색어(GET /api/v1/product/races/suggestions): 대회·종목 각각 5개까지, 대회 중복 없음, 공개 대회만,
 *     검색어 빈 값·없음 400</li>
 * </ul>
 * nori는 "서울숲"을 한 단어로 두므로, 기본 요청의 장소("서울숲 일대")·소개는 검색어 '서울'과 맞지 않는다.
 * 검색어 테스트는 다른 대회와 겹치지 않게 대회명·장소·지역·소개를 모두 바꿔 등록한다.
 */
class ApiV1RaceSearchTest extends ProductIntegrationTest {
	private static final String RACES = "/api/v1/product/races";
	private static final String SUGGESTIONS = "/api/v1/product/races/suggestions";
	private static final String SEARCH_SUCCESS = "대회 목록을 조회했습니다.";
	private static final String SUGGEST_SUCCESS = "연관검색어를 조회했습니다.";
	private static final String INVALID_VALUE_TYPE = "요청 값의 형식이 올바르지 않습니다.";
	private static final String PAGE_TOO_LARGE = "페이지 번호가 너무 큽니다.";
	private static final String PRICE_NEGATIVE = "참가비 범위는 0원 이상이어야 합니다.";
	private static final String KEYWORD_BLANK = "검색어를 작성해주세요.";

	// ---------- 공개 조건, 한 줄 ----------

	@Test
	@DisplayName("검색어 없이 조회하면 판매 중 대회와 판매 예정(개인 예매 이틀 뒤 오픈) 대회가 나오고, "
		+ "접수 마감이 하루 지난 판매 중 대회는 빠진다")
	void listsOnlyPublicRaces() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace onSale = registerOnSale(race().title("판매 중 대회")
			.applicationCloseAt(now.plusDays(5))
			.build());
		RegisteredRace scheduled = register(race().title("판매 예정 대회")
			.individualOpenAt(now.plusDays(2))
			.applicationCloseAt(now.plusDays(8))
			.build());
		// 사흘 전에 개인 예매가 열려 판매 중이지만, 접수 마감이 하루 지났다
		RegisteredRace closed = registerOnSale(race().title("접수 마감 대회")
			.individualOpenAt(now.minusDays(3))
			.applicationCloseAt(now.minusDays(1))
			.build());
		// 빠지는 대회도 검색 문서는 있다 (색인되지 않아서 빠진 것이 아님을 먼저 확인한다)
		expectDocumentSaleStatus(closed, RaceSaleStatus.ON_SALE);

		expectRacesInOrder(search(), onSale, scheduled)
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value(SEARCH_SUCCESS))
			.andExpect(jsonPath("$.data.races[*].saleStatus", contains("ON_SALE", "SCHEDULED")));
	}

	@Test
	@DisplayName("종목 3개(10K 40,000원·하프 55,000원·5K 30,000원) 대회도 한 줄로 나와 totalElements는 대회 수(2)이고, "
		+ "그 줄에는 종목 3개가 종목 ID 순으로, 최저가는 30,000원으로 담긴다")
	void listsMultiCourseRaceAsOneRow() throws Exception {
		RaceBuilder builder = race().title("2026 춘천 마라톤")
			.location("의암호 일대")
			.region("강원특별자치도", "춘천시")
			.description("호수를 따라 달리는 대회")
			.course(course().price(40_000), halfCourse().price(55_000), fiveKmCourse().price(30_000));
		LocalDateTime now = builder.now();
		RegisteredRace multi = registerOnSale(builder
			.raceDate(now.plusDays(20))
			.applicationCloseAt(now.plusDays(18))
			.build());
		// 기본 요청은 마감이 10일 뒤라 마감 임박순으로 앞에 온다
		RegisteredRace single = registerOnSale(race().build());

		ResultActions result = expectRacesInOrder(search(), single, multi)
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalPages").value(1))
			.andExpect(jsonPath("$.data.races[0].minPrice").value(45_000))
			.andExpect(jsonPath("$.data.races[1].title").value("2026 춘천 마라톤"))
			.andExpect(jsonPath("$.data.races[1].location").value("의암호 일대"))
			.andExpect(jsonPath("$.data.races[1].regionSido").value("강원특별자치도"))
			.andExpect(jsonPath("$.data.races[1].regionSigungu").value("춘천시"))
			.andExpect(jsonPath("$.data.races[1].saleStatus").value("ON_SALE"))
			.andExpect(jsonPath("$.data.races[1].minPrice").value(30_000))
			.andExpect(jsonPath("$.data.races[1].courses", hasSize(3)))
			.andExpect(jsonPath("$.data.races[1].courses[*].raceCourseId",
				contains(ids(multi.courseIds()))))
			.andExpect(jsonPath("$.data.races[1].courses[*].name", contains("10K", "하프", "5K")))
			.andExpect(jsonPath("$.data.races[1].courses[*].courseType", contains("KM_10", "HALF", "KM_5")))
			.andExpect(jsonPath("$.data.races[1].courses[*].price", contains(40_000, 55_000, 30_000)))
			.andExpect(jsonPath("$.data.races[1].courses[*].status", contains("OPEN", "OPEN", "OPEN")))
			.andExpect(jsonPath("$.data.races[1].courses[*].individualRemaining", contains(100, 100, 100)))
			.andExpect(jsonPath("$.data.races[1].courses[0].distanceKm").value(10.0))
			.andExpect(jsonPath("$.data.races[1].courses[1].distanceKm").value(21.1))
			.andExpect(jsonPath("$.data.races[1].courses[2].distanceKm").value(5.0));

		String body = bodyOf(result);
		assertThat(dateTimeAt(body, "$.data.races[1].raceDate")).isEqualTo(now.plusDays(20));
		assertThat(dateTimeAt(body, "$.data.races[1].applicationCloseAt")).isEqualTo(now.plusDays(18));
		// 크루 예매가 없으면 개인 예매 오픈(기본 한 시간 전) 날짜가 판매 시작일이다
		// 구매자 화면용으로 크루 예매 기간(없으면 null)과 개인 예매 오픈 일시도 준다
		assertThat(LocalDate.parse(JsonPath.<String>read(body, "$.data.races[1].saleOpenDate")))
			.isEqualTo(now.minusHours(1).toLocalDate());
		assertThat(dateTimeAt(body, "$.data.races[1].individualOpenAt")).isEqualTo(now.minusHours(1));
		assertThat((Object)JsonPath.read(body, "$.data.races[1].crewSaleStartAt")).isNull();
		assertThat((Object)JsonPath.read(body, "$.data.races[1].crewSaleEndAt")).isNull();

		// 종목 3개가 모두 맞는 검색어도 대회는 한 줄이다
		expectRacesInOrder(search("keyword", "춘천"), multi);
		// 10K·하프 두 종목이 맞는 대회와 10K 한 종목이 맞는 대회도 한 줄씩이다
		expectRacesInAnyOrder(search("courseType", "KM_10", "courseType", "HALF"), single, multi);
	}

	// ---------- 정렬 ----------

	@Test
	@DisplayName("정렬을 보내지 않으면 검색어 '단풍'이 있을 때 정확도순(대회명에 단풍이 든 마감 12일 뒤 대회 먼저), "
		+ "검색어가 없거나 공백이면 마감 임박순(소개에 단풍이 든 마감 5일 뒤 대회 먼저)이다")
	void defaultSortDependsOnKeyword() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace titleMatched = registerOnSale(race().title("2026 단풍 마라톤")
			.location("내장산 일대")
			.region("전북특별자치도", "정읍시")
			.description("숲길을 달리는 대회")
			.applicationCloseAt(now.plusDays(12))
			.build());
		RegisteredRace descriptionMatched = registerOnSale(race().title("2026 숲길 마라톤")
			.location("내장산 일대")
			.region("전북특별자치도", "정읍시")
			.description("단풍 길을 달리는 대회")
			.applicationCloseAt(now.plusDays(5))
			.build());

		expectRacesInOrder(search("keyword", "단풍"), titleMatched, descriptionMatched);
		expectRacesInOrder(search("keyword", "단풍", "sort", "RELEVANCE"), titleMatched, descriptionMatched);
		expectRacesInOrder(search("keyword", "단풍", "sort", "DEADLINE"), descriptionMatched, titleMatched);
		expectRacesInOrder(search(), descriptionMatched, titleMatched);
		expectRacesInOrder(search("keyword", "  "), descriptionMatched, titleMatched);
	}

	@Test
	@DisplayName("정렬 5가지: 마감 임박순은 마감 5·10·20일 뒤 순, 대회일 빠른순은 대회 12·25·30일 뒤 순, 최신 등록순은 등록 역순, "
		+ "가격 낮은순은 30,000·40,000·50,000원 순이고, 검색어 없는 정확도순은 마감 임박순과 같다")
	void sortsByEachOption() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		// 정렬마다 순서가 달라지게 마감·대회일·등록 순서·참가비를 엇갈려 둔다
		RegisteredRace first = registerOnSale(race().title("첫 번째 등록 대회")
			.raceDate(now.plusDays(30))
			.applicationCloseAt(now.plusDays(5))
			.course(course().price(30_000))
			.build());
		RegisteredRace second = registerOnSale(race().title("두 번째 등록 대회")
			.raceDate(now.plusDays(12))
			.applicationCloseAt(now.plusDays(10))
			.course(course().price(50_000))
			.build());
		RegisteredRace third = registerOnSale(race().title("세 번째 등록 대회")
			.raceDate(now.plusDays(25))
			.applicationCloseAt(now.plusDays(20))
			.course(course().price(40_000))
			.build());

		expectRacesInOrder(search(), first, second, third);
		expectRacesInOrder(search("sort", "DEADLINE"), first, second, third);
		expectRacesInOrder(search("sort", "RACE_DATE"), second, third, first);
		expectRacesInOrder(search("sort", "LATEST"), third, second, first);
		expectRacesInOrder(search("sort", "PRICE"), first, third, second)
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(30_000, 40_000, 50_000)));
		expectRacesInOrder(search("sort", "RELEVANCE"), first, second, third);
	}

	@Test
	@DisplayName("검색어 '은하'가 대회명에 든 대회(3배) > 장소에 든 대회(2배) > 소개에 든 대회(1배) 순서로 나오고, "
		+ "마감 임박순(소개 4일·장소 8일·대회명 12일 뒤)과 반대여도 점수 순서를 따른다")
	void relevanceWeightsTitleOverLocationOverDescription() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace inDescription = registerOnSale(nightRun("도심 나이트 런", "여의도 공원", "은하를 따라 달리는 야간 대회")
			.applicationCloseAt(now.plusDays(4))
			.build());
		RegisteredRace inLocation = registerOnSale(nightRun("도심 나이트 런", "은하 공원", "도심을 달리는 야간 대회")
			.applicationCloseAt(now.plusDays(8))
			.build());
		RegisteredRace inTitle = registerOnSale(nightRun("은하 나이트 런", "여의도 공원", "도심을 달리는 야간 대회")
			.applicationCloseAt(now.plusDays(12))
			.build());

		expectRacesInOrder(search("keyword", "은하"), inTitle, inLocation, inDescription);
		expectRacesInOrder(search("keyword", "은하", "sort", "DEADLINE"), inDescription, inLocation, inTitle);
	}

	@Test
	@DisplayName("최신 등록순은 대회 등록 일시 기준이라, 먼저 등록한 대회에 나중에 하프 종목을 더해도 나중에 등록한 대회가 앞에 나온다")
	void latestSortUsesRaceCreatedAt() throws Exception {
		RaceBuilder earlierBuilder = race().title("먼저 등록한 대회");
		RegisteredRace earlier = registerOnSale(earlierBuilder.build());
		RegisteredRace later = registerOnSale(race().title("나중에 등록한 대회").build());
		expectRacesInOrder(search("sort", "LATEST"), later, earlier);

		// 판매 중인 대회라 기존 10K 종목은 그대로 두고 하프 종목만 더한다
		putJson(RACES + "/" + earlier.raceId(), SELLER_MEMBER_ID,
			earlierBuilder.course(course().courseId(earlier.courseId()), halfCourse()).build())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회를 수정했습니다."))
			.andExpect(jsonPath("$.data.courseIds", hasSize(2)));
		syncRaceDocuments();

		// 더한 하프 종목도 색인되었다 (하프만 고르면 먼저 등록한 대회만 나온다)
		expectRacesInOrder(search("courseType", "HALF", "sort", "LATEST"), earlier);
		expectRacesInOrder(search("sort", "LATEST"), later, earlier)
			.andExpect(jsonPath("$.data.races[1].courses", hasSize(2)));
	}

	// ---------- 검색어 ----------

	@Test
	@DisplayName("검색어 '부산 야경'은 단어 하나만 맞아도 나와 부산만 맞는 대회·야경만 맞는 대회도 나오고, "
		+ "둘 다 맞는 대회가 정확도순 맨 앞이며, 둘 다 없는 대전 대회는 빠진다")
	void matchesAnyWord() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		// 둘 다 맞는 대회는 마감이 가장 늦어, 맨 앞에 온 것이 마감 순서 때문이 아님을 확인한다
		RegisteredRace both = registerOnSale(race().title("부산 야경 마라톤")
			.region("부산광역시", "해운대구")
			.location("해운대 해변")
			.description("바다를 보며 달리는 대회")
			.applicationCloseAt(now.plusDays(12))
			.build());
		RegisteredRace busanOnly = registerOnSale(race().title("부산 바다 마라톤")
			.region("부산광역시", "수영구")
			.location("광안리 해변")
			.description("바다를 보며 달리는 대회")
			.applicationCloseAt(now.plusDays(5))
			.build());
		RegisteredRace nightViewOnly = registerOnSale(race().title("서울 야경 마라톤")
			.location("잠수교 일대")
			.description("도심을 달리는 대회")
			.applicationCloseAt(now.plusDays(7))
			.build());
		registerOnSale(race().title("대전 숲길 마라톤")
			.region("대전광역시", "유성구")
			.location("갑천 일대")
			.description("숲길을 달리는 대회")
			.applicationCloseAt(now.plusDays(3))
			.build());

		expectRacesInAnyOrder(search("keyword", "부산 야경"), both, busanOnly, nightViewOnly)
			.andExpect(jsonPath("$.data.races[0].raceId").value(both.raceId()));
	}

	@Test
	@DisplayName("검색어 '충남'은 동의어로 지역이 충청남도인 대회를 찾고, 충청북도·서울 대회는 찾지 않는다")
	void expandsRegionSynonym() throws Exception {
		RegisteredRace chungnam = registerOnSale(race().title("공주 백제 마라톤")
			.region("충청남도", "공주시")
			.location("공산성 일대")
			.description("강변을 달리는 대회")
			.build());
		registerOnSale(race().title("청주 직지 마라톤")
			.region("충청북도", "청주시")
			.location("무심천 일대")
			.description("강변을 달리는 대회")
			.build());
		registerOnSale(race().build());

		expectRacesInOrder(search("keyword", "충남"), chungnam);
	}

	@Test
	@DisplayName("4글자 오타 '트레일러'는 한 글자 다른 트레일런 대회를 찾고, 2글자 오타 '서을'은 오타를 허용하지 않아 "
		+ "서울 대회를 찾지 않는다('서울'로는 찾는다)")
	void allowsTypoByWordLength() throws Exception {
		RegisteredRace trail = registerOnSale(race().title("계룡산 트레일런")
			.region("충청남도", "공주시")
			.location("계룡산 국립공원")
			.description("산길을 달리는 대회")
			.course(course().surface(CourseSurface.TRAIL))
			.build());
		RegisteredRace seoul = registerOnSale(race().build());

		expectRacesInOrder(search("keyword", "트레일러"), trail);
		expectNoRaces(search("keyword", "서을"));
		expectRacesInOrder(search("keyword", "서울"), seoul);
	}

	// ---------- 필터 ----------

	@Test
	@DisplayName("시·도 서울특별시는 서울 대회 3개, 서울특별시·중구는 서울 중구 대회만 찾고, 시·군·구 중구만 보내면 "
		+ "서울 중구·부산 중구 대회가 모두 나오며, 글자가 똑같지 않은 '부산'은 0건이다")
	void filtersByRegion() throws Exception {
		RegisteredRace seongdong = registerOnSale(race().build());
		RegisteredRace mapo = registerOnSale(race().title("2026 마포 가을 런")
			.region("서울특별시", "마포구")
			.location("월드컵공원 일대")
			.build());
		RegisteredRace seoulJung = registerOnSale(race().title("2026 청계천 런")
			.region("서울특별시", "중구")
			.location("청계광장")
			.build());
		RegisteredRace busanJung = registerOnSale(race().title("2026 부산 바다 런")
			.region("부산광역시", "중구")
			.location("용두산 공원")
			.build());

		expectRacesInAnyOrder(search("regionSido", "서울특별시"), seongdong, mapo, seoulJung);
		expectRacesInAnyOrder(search("regionSido", "서울특별시", "regionSigungu", "중구"), seoulJung);
		expectRacesInAnyOrder(search("regionSigungu", "중구"), seoulJung, busanJung);
		expectRacesInAnyOrder(search("regionSido", "부산광역시"), busanJung);
		expectNoRaces(search("regionSido", "부산"));
	}

	@Test
	@DisplayName("종목 구분을 HALF·빈 값·FULL로 보내면 빈 값은 빼고 하프나 풀 종목이 하나라도 있는 대회만 나오고, "
		+ "종목 구분·노면·정렬을 모두 빈 값으로 보내면 조건 없이 대회 4개가 모두 나온다")
	void filtersByCourseTypes() throws Exception {
		RegisteredRace tenKm = registerOnSale(race().title("10K 대회").build());
		RegisteredRace half = registerOnSale(race().title("하프 대회").course(halfCourse()).build());
		RegisteredRace fiveKmAndFull = registerOnSale(race().title("5K와 풀 대회")
			.course(fiveKmCourse(), fullCourse())
			.build());
		RegisteredRace ultra = registerOnSale(race().title("울트라 대회").course(ultraCourse()).build());

		expectRacesInAnyOrder(search("courseType", "HALF", "courseType", "", "courseType", "FULL"),
			half, fiveKmAndFull);
		expectRacesInAnyOrder(search("courseType", "KM_5"), fiveKmAndFull);
		expectRacesInAnyOrder(search("courseType", "ULTRA"), ultra);
		expectRacesInAnyOrder(search("courseType", "", "surface", "", "sort", ""),
			tenKm, half, fiveKmAndFull, ultra);
	}

	@Test
	@DisplayName("노면 TRAIL은 트레일 종목이 있는 대회만, 거리 10(10.00도 같다)은 10km 종목이 있는 대회만, "
		+ "21.1은 하프 대회만 찾고, 거리 21처럼 똑같지 않은 값은 0건이다")
	void filtersBySurfaceAndDistance() throws Exception {
		RegisteredRace roadTenKm = registerOnSale(race().title("로드 10K 대회").build());
		RegisteredRace trailTenKm = registerOnSale(race().title("트레일 10K 대회")
			.course(course().surface(CourseSurface.TRAIL))
			.build());
		RegisteredRace roadHalf = registerOnSale(race().title("로드 하프 대회").course(halfCourse()).build());

		expectRacesInAnyOrder(search("surface", "TRAIL"), trailTenKm);
		expectRacesInAnyOrder(search("surface", "ROAD"), roadTenKm, roadHalf);
		expectRacesInAnyOrder(search("distanceKm", "10"), roadTenKm, trailTenKm);
		expectRacesInAnyOrder(search("distanceKm", "10.00"), roadTenKm, trailTenKm);
		expectRacesInAnyOrder(search("distanceKm", "21.1"), roadHalf);
		expectRacesInAnyOrder(search("surface", "ROAD", "distanceKm", "10"), roadTenKm);
		expectNoRaces(search("distanceKm", "21"));
	}

	@Test
	@DisplayName("참가비 최소 40,000원만 보내면 40,000원 이상 종목이 있는 대회(최저가 50,000·60,000원), "
		+ "최대 40,000원만 보내면 40,000원 이하 종목이 있는 대회(최저가 20,000·35,000원)가 나오고, "
		+ "35,000~50,000원은 양 끝 값을 포함한다")
	void filtersByPriceOnOneSide() throws Exception {
		PricedRaces races = registerPricedRaces();

		// 5K 20,000원·하프 60,000원 대회는 조건에 맞은 하프 60,000원이 최저가다
		expectRacesInOrder(search("minPrice", "40000", "sort", "PRICE"), races.half(), races.fiveKmAndHalf())
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(50_000, 60_000)));
		expectRacesInOrder(search("maxPrice", "40000", "sort", "PRICE"), races.fiveKmAndHalf(), races.tenKm())
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(20_000, 35_000)));
		expectRacesInOrder(search("minPrice", "35000", "maxPrice", "50000", "sort", "PRICE"),
			races.tenKm(), races.half())
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(35_000, 50_000)));
	}

	@Test
	@DisplayName("대회 날짜 범위는 시작·끝 날짜를 모두 포함해, 20일 뒤~20일 뒤면 20일 뒤 대회만, 시작만 20일 뒤면 20·30일 뒤 대회, "
		+ "끝만 20일 뒤면 10·20일 뒤 대회가 나오고, 21일 뒤~29일 뒤면 0건이다")
	void filtersByRaceDateRange() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace in10Days = registerOnSale(raceOn(now.plusDays(10)));
		RegisteredRace in20Days = registerOnSale(raceOn(now.plusDays(20)));
		RegisteredRace in30Days = registerOnSale(raceOn(now.plusDays(30)));
		String day20 = now.plusDays(20).toLocalDate().toString();

		expectRacesInAnyOrder(search("raceDateFrom", day20, "raceDateTo", day20), in20Days);
		expectRacesInAnyOrder(search("raceDateFrom", day20), in20Days, in30Days);
		expectRacesInAnyOrder(search("raceDateTo", day20), in10Days, in20Days);
		expectNoRaces(search("raceDateFrom", now.plusDays(21).toLocalDate().toString(),
			"raceDateTo", now.plusDays(29).toLocalDate().toString()));
	}

	// ---------- 가격 낮은순, 최저가 ----------

	@Test
	@DisplayName("가격 낮은순과 최저가는 조건에 맞은 종목 중 가장 싼 값이라, 조건이 없으면 5K 20,000원 대회가 맨 앞이지만 "
		+ "하프만 고르면 그 대회는 하프 60,000원으로 매겨져 하프 50,000원 대회 뒤로 간다")
	void priceSortUsesCheapestMatchedCourse() throws Exception {
		PricedRaces races = registerPricedRaces();

		expectRacesInOrder(search("sort", "PRICE"), races.fiveKmAndHalf(), races.tenKm(), races.half())
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(20_000, 35_000, 50_000)));
		expectRacesInOrder(search("courseType", "HALF", "sort", "PRICE"), races.half(), races.fiveKmAndHalf())
			.andExpect(jsonPath("$.data.races[*].minPrice", contains(50_000, 60_000)))
			// 줄에는 조건과 상관없이 대회의 종목이 모두 담긴다
			.andExpect(jsonPath("$.data.races[1].courses[*].name", contains("5K", "하프")));
	}

	// ---------- 페이지 ----------

	@Test
	@DisplayName("대회 5개를 2개씩 나누면 totalPages는 3이고 0·1·2페이지에 마감 임박순으로 2·2·1개가 나오며, "
		+ "3페이지는 빈 목록이지만 totalElements는 5다")
	void paginates() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		// 마감이 늦은 대회부터 등록해, 등록 순서(대회 ID)와 마감 임박순이 반대가 되게 한다
		List<RegisteredRace> byDeadline = new ArrayList<>();
		for (int days = 5; days >= 1; days--) {
			byDeadline.addFirst(registerOnSale(race().title("마감 %d일 뒤 대회".formatted(days))
				.applicationCloseAt(now.plusDays(days))
				.build()));
		}

		expectPageOfFive(search("page", "0", "size", "2"), 0, byDeadline.get(0), byDeadline.get(1));
		expectPageOfFive(search("page", "1", "size", "2"), 1, byDeadline.get(2), byDeadline.get(3));
		expectPageOfFive(search("page", "2", "size", "2"), 2, byDeadline.get(4));
		search("page", "3", "size", "2")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races", hasSize(0)))
			.andExpect(jsonPath("$.data.page").value(3))
			.andExpect(jsonPath("$.data.size").value(2))
			.andExpect(jsonPath("$.data.totalElements").value(5))
			.andExpect(jsonPath("$.data.totalPages").value(3));
	}

	// ---------- 목록 400 ----------

	@Test
	@DisplayName("페이지 크기 101은 '한 번에 100개까지 조회할 수 있습니다.', 0은 '페이지 크기는 1 이상이어야 합니다.', "
		+ "페이지 번호 -1은 '페이지 번호는 0 이상이어야 합니다.'로 400이고, 크기 100은 200이다")
	void rejectsOutOfRangePageOrSize() throws Exception {
		expectBadRequest(search("size", "101"), "한 번에 100개까지 조회할 수 있습니다.");
		expectBadRequest(search("size", "0"), "페이지 크기는 1 이상이어야 합니다.");
		expectBadRequest(search("page", "-1"), "페이지 번호는 0 이상이어야 합니다.");
		search("size", "100")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(100));
	}

	@Test
	@DisplayName("ES가 돌려주는 1만 번째 결과까지는 조회되어 크기 100의 99페이지는 200이고, "
		+ "크기 100의 100페이지·크기 1의 10000페이지(10,001번째)는 '페이지 번호가 너무 큽니다.'로 400이다")
	void rejectsPageBeyondResultWindow() throws Exception {
		search("page", "99", "size", "100")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races", hasSize(0)))
			.andExpect(jsonPath("$.data.page").value(99));
		expectBadRequest(search("page", "100", "size", "100"), PAGE_TOO_LARGE);
		expectBadRequest(search("page", "10000", "size", "1"), PAGE_TOO_LARGE);
	}

	@Test
	@DisplayName("페이지 번호가 int 최댓값(2147483647)이어도 '페이지 번호가 너무 큽니다.'로 400이다")
	void rejectsMaxIntPage() throws Exception {
		expectBadRequest(search("page", String.valueOf(Integer.MAX_VALUE)), PAGE_TOO_LARGE);
	}

	@Test
	@DisplayName("참가비 최소·최대가 -1원이면 '참가비 범위는 0원 이상이어야 합니다.'로 400이고, 0원은 200이다")
	void rejectsNegativePrice() throws Exception {
		expectBadRequest(search("minPrice", "-1"), PRICE_NEGATIVE);
		expectBadRequest(search("maxPrice", "-1"), PRICE_NEGATIVE);
		search("minPrice", "0", "maxPrice", "0").andExpect(status().isOk());
	}

	@ParameterizedTest(name = "{0}={1} → 400")
	@CsvSource({
		"sort, NAME",
		"courseType, MARATHON",
		"surface, MUD",
		"distanceKm, ten",
		"minPrice, abc",
		"maxPrice, 1.5",
		"raceDateFrom, 2026-13-01",
		"raceDateTo, 2026/11/30",
		"page, abc",
		"size, abc"
	})
	@DisplayName("정렬·종목 구분·노면·거리·참가비·대회 날짜·페이지 값의 형식이 틀리면 '요청 값의 형식이 올바르지 않습니다.'로 400이다")
	void rejectsInvalidValueType(String name, String value) throws Exception {
		expectBadRequest(search(name, value), INVALID_VALUE_TYPE);
	}

	// ---------- 연관검색어 ----------

	@Test
	@DisplayName("'서울'로 시작하는 대회 6개(종목 1개씩)가 있으면 대회 연관검색어 5개, 종목 연관검색어 5개까지만 주고 "
		+ "대회는 겹치지 않는다")
	void suggestsUpToFiveEach() throws Exception {
		List<RegisteredRace> races = new ArrayList<>();
		for (int number = 1; number <= 6; number++) {
			races.add(registerOnSale(race().title("서울 러닝 시리즈 " + number).build()));
		}
		List<Long> raceIds = races.stream().map(RegisteredRace::raceId).toList();
		List<Long> courseIds = races.stream().map(RegisteredRace::courseId).toList();

		String body = bodyOf(suggest("서울")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value(SUGGEST_SUCCESS))
			.andExpect(jsonPath("$.data.races", hasSize(5)))
			.andExpect(jsonPath("$.data.races[*].title", everyItem(startsWith("서울 러닝 시리즈 "))))
			.andExpect(jsonPath("$.data.courses", hasSize(5)))
			.andExpect(jsonPath("$.data.courses[*].label", everyItem(endsWith(" 10K")))));

		List<Long> suggestedRaceIds = JsonIds.readLongs(body, "$.data.races[*].raceId");
		assertThat(suggestedRaceIds).doesNotHaveDuplicates().isSubsetOf(raceIds);
		List<Long> suggestedCourseIds = JsonIds.readLongs(body, "$.data.courses[*].raceCourseId");
		assertThat(suggestedCourseIds).doesNotHaveDuplicates().isSubsetOf(courseIds);
	}

	@Test
	@DisplayName("종목 3개(10K·하프·5K) 대회는 대회 연관검색어에 한 번만 나오고, 종목 연관검색어에는 "
		+ "'2026 서울 하프 마라톤 10K'처럼 '대회명 종목명' 3개가 모두 나온다")
	void suggestsRaceOnceWithAllCourses() throws Exception {
		RegisteredRace multi = registerOnSale(race().course(course(), halfCourse(), fiveKmCourse()).build());
		RegisteredRace single = registerOnSale(race().title("서울 야간 레이스").build());

		String body = bodyOf(suggest("서울")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId", containsInAnyOrder(id(multi.raceId()), id(single.raceId()))))
			.andExpect(jsonPath("$.data.races[*].title",
				containsInAnyOrder("2026 서울 하프 마라톤", "서울 야간 레이스"))));

		List<Map<String, Object>> courses = JsonPath.read(body, "$.data.courses");
		assertThat(courses)
			.extracting(suggestion -> asLong(suggestion.get("raceCourseId")),
				suggestion -> asLong(suggestion.get("raceId")), suggestion -> suggestion.get("label"))
			.containsExactlyInAnyOrder(
				tuple(multi.courseIds().get(0), multi.raceId(), "2026 서울 하프 마라톤 10K"),
				tuple(multi.courseIds().get(1), multi.raceId(), "2026 서울 하프 마라톤 하프"),
				tuple(multi.courseIds().get(2), multi.raceId(), "2026 서울 하프 마라톤 5K"),
				tuple(single.courseId(), single.raceId(), "서울 야간 레이스 10K"));
	}

	@Test
	@DisplayName("연관검색어는 판매 중·판매 예정 대회에서만 찾고, 접수 마감이 하루 지난 대회는 대회·종목 모두 빠진다")
	void suggestsOnlyPublicRaces() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace onSale = registerOnSale(race().title("서울 가을 마라톤").build());
		RegisteredRace scheduled = register(race().title("서울 겨울 마라톤")
			.individualOpenAt(now.plusDays(2))
			.build());
		registerOnSale(race().title("서울 여름 마라톤")
			.individualOpenAt(now.minusDays(3))
			.applicationCloseAt(now.minusDays(1))
			.build());

		suggest("서울")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId",
				containsInAnyOrder(id(onSale.raceId()), id(scheduled.raceId()))))
			.andExpect(jsonPath("$.data.courses[*].raceId",
				containsInAnyOrder(id(onSale.raceId()), id(scheduled.raceId()))));
	}

	@Test
	@DisplayName("연관검색어의 검색어가 빈 값이나 공백이면 '검색어를 작성해주세요.', 검색어 파라미터가 없으면 "
		+ "'필수 요청 값(keyword)이 없습니다.'로 400이다")
	void rejectsMissingSuggestKeyword() throws Exception {
		expectBadRequest(suggest(""), KEYWORD_BLANK);
		expectBadRequest(suggest("  "), KEYWORD_BLANK);
		expectBadRequest(mockMvc.perform(get(SUGGESTIONS)), "필수 요청 값(keyword)이 없습니다.");
	}

	// ---------- 도우미 ----------

	// 참가비가 다른 대회 3개: 5K 20,000원·하프 60,000원 / 하프 50,000원 / 10K 35,000원
	private record PricedRaces(RegisteredRace fiveKmAndHalf, RegisteredRace half, RegisteredRace tenKm) {
	}

	private PricedRaces registerPricedRaces() throws Exception {
		RegisteredRace fiveKmAndHalf = registerOnSale(race().title("5K와 하프 대회")
			.course(fiveKmCourse().price(20_000), halfCourse().price(60_000))
			.build());
		RegisteredRace half = registerOnSale(race().title("하프 대회").course(halfCourse().price(50_000)).build());
		RegisteredRace tenKm = registerOnSale(race().title("10K 대회").course(course().price(35_000)).build());
		return new PricedRaces(fiveKmAndHalf, half, tenKm);
	}

	// 이름·값을 번갈아 받아 쿼리 파라미터로 붙인다. 같은 이름을 여러 번 넣으면 값이 여러 개가 된다 (courseType)
	private ResultActions search(String... nameValues) throws Exception {
		MockHttpServletRequestBuilder request = get(RACES);
		for (int i = 0; i < nameValues.length; i += 2) {
			request.param(nameValues[i], nameValues[i + 1]);
		}
		return mockMvc.perform(request);
	}

	private ResultActions suggest(String keyword) throws Exception {
		return mockMvc.perform(get(SUGGESTIONS).param("keyword", keyword));
	}

	// 대회의 (첫) 종목 검색 문서가 이 판매 상태로 색인되어 있다
	private void expectDocumentSaleStatus(RegisteredRace registered, RaceSaleStatus saleStatus) {
		assertThat(raceCourseDocumentRepository.findById(String.valueOf(registered.courseId())))
			.hasValueSatisfying(document -> assertThat(document.getSaleStatus()).isEqualTo(saleStatus));
	}

	// 서울 영등포구의 야간 대회. 검색어가 대회명·장소·소개 중 어디에 들었는지만 다르게 둔다
	private static RaceBuilder nightRun(String title, String location, String description) {
		return race().title(title)
			.region("서울특별시", "영등포구")
			.location(location)
			.description(description);
	}

	// 대회 일시만 다른 대회 (접수 마감은 모두 9일 뒤)
	private static RaceSaveRequestDto raceOn(LocalDateTime raceDate) {
		RaceBuilder builder = race().title("%s 대회".formatted(raceDate.toLocalDate()));
		return builder.raceDate(raceDate)
			.applicationCloseAt(builder.now().plusDays(9))
			.build();
	}

	private static CourseBuilder halfCourse() {
		return course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180);
	}

	private static CourseBuilder fiveKmCourse() {
		return course().name("5K").type(CourseType.KM_5).distanceKm("5").timeLimitMinutes(60);
	}

	// 거리는 소수 둘째 자리까지만 받으므로 42.195km 대신 42.19km로 둔다
	private static CourseBuilder fullCourse() {
		return course().name("풀").type(CourseType.FULL).distanceKm("42.19").timeLimitMinutes(360);
	}

	private static CourseBuilder ultraCourse() {
		return course().name("울트라 100K").type(CourseType.ULTRA).distanceKm("100").timeLimitMinutes(960);
	}

	// 조회에 성공했고 대회가 이 순서로 나왔다 (totalElements도 대회 수와 같다)
	private static ResultActions expectRacesInOrder(ResultActions result, RegisteredRace... races) throws Exception {
		return result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId", contains(idsOf(races))))
			.andExpect(jsonPath("$.data.totalElements").value(races.length));
	}

	// 조회에 성공했고 대회가 순서와 상관없이 이것들만 나왔다
	private static ResultActions expectRacesInAnyOrder(ResultActions result, RegisteredRace... races)
		throws Exception {
		return result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId", containsInAnyOrder(idsOf(races))))
			.andExpect(jsonPath("$.data.totalElements").value(races.length));
	}

	private static void expectNoRaces(ResultActions result) throws Exception {
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races", hasSize(0)))
			.andExpect(jsonPath("$.data.totalElements").value(0))
			.andExpect(jsonPath("$.data.totalPages").value(0));
	}

	// 대회 5개를 2개씩 나눈 목록의 한 페이지
	private static void expectPageOfFive(ResultActions result, int page, RegisteredRace... races) throws Exception {
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId", contains(idsOf(races))))
			.andExpect(jsonPath("$.data.page").value(page))
			.andExpect(jsonPath("$.data.size").value(2))
			.andExpect(jsonPath("$.data.totalElements").value(5))
			.andExpect(jsonPath("$.data.totalPages").value(3));
	}

	private static void expectBadRequest(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.data", nullValue()));
	}

	private static List<Matcher<? super Object>> idsOf(RegisteredRace... races) {
		return ids(Arrays.stream(races).map(RegisteredRace::raceId).toList());
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
