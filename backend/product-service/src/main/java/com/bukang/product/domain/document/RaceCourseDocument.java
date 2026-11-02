package com.bukang.product.domain.document;

import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.CourseSurface;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 검색 문서. 종목 1개 = 문서 1개이고, 대회 정보는 종목 문서마다 복사해 넣는다.
 * 대회 목록은 raceId로 묶어(collapse) 보여 주고, 연관검색어는 raceTitle(대회)과 courseLabel(종목)을 나눠 찾는다.
 * 남은 정원·종목 상태(접수 중·정원 마감)처럼 자주 바뀌는 값은 넣지 않고 DB에서 읽는다.
 * 분석기·동의어는 race-settings.json에 있다. 동의어는 검색할 때만 적용한다 (searchAnalyzer).
 */
@Getter
@NoArgsConstructor(access = PROTECTED)
@Document(indexName = "race_courses")
@Setting(settingPath = "/elasticsearch/race-settings.json")
public class RaceCourseDocument extends BaseDocument {
	// 분석기 3개 (race-settings.json). 모두 html_strip → nori 토크나이저 → 조사·어미 제거 → 한자를 한글로 → 소문자 순서다
	// 사용자 사전(키로, 풀코스, 하프마라톤, 트레일러닝, 트레일런, 울트라마라톤)은 쪼개지 않는다
	// (사전이 없으면 "10키로"가 10, 키, 로로 잘린다)

	// 색인: 원래 단어와 쪼갠 조각을 함께 넣는다 (decompound_mode mixed)
	//   "계룡산 트레일런 10K" → 계룡산, 계룡, 산, 트레일런, 10, k
	//   "충청남도 공주시"     → 충청남도, 충청, 남도, 공주시, 공주, 시
	//   원래 단어(계룡산, 충청남도)는 검색이 조각만 만들어서 실제 검색에서는 맞지 않는다
	private static final String INDEX_ANALYZER = "race_index_analyzer";
	// 검색: 쪼갠 조각만 만들고(discard) 동의어를 펼친다
	//   mixed로 하지 않은 이유: 동의어 필터는 규칙("충남, 충청남도")의 단어도 이 토크나이저로 먼저 쪼갠다.
	//   mixed로 쪼개면 "충청남도"가 같은 자리에서 원본과 쪼갠 조각, 두 갈래로 갈라진다.
	//     충청남도          ← 원본 갈래
	//     충청 → 남도       ← 쪼갠 조각 갈래
	//   동의어 필터는 규칙을 한 갈래로만 저장할 수 있어서, 원본과 조각 중 어느 쪽으로 만들지 정하지 못하고 규칙을 버린다.
	//   그래서 "충남"으로 검색해도 충청남도로 펼쳐지지 않아 충청남도 대회가 나오지 않았다.
	//   discard는 "충청남도"를 충청 → 남도 한 갈래로만 쪼개므로 규칙이 만들어진다.
	//   "충남 트레일" → 충청, 충남, 남도, 트레일, 트레일런, 트레일러닝 → 색인의 충청·남도·트레일런과 맞는다
	//   "10키로"      → 10, k, km, 키로, 킬로                         → 색인의 10·k와 맞는다
	//   동의어는 색인에 넣지 않으므로, 바꿔도 인덱스를 다시 만들지 않는다 (updateable)
	private static final String SEARCH_ANALYZER = "race_search_analyzer";
	// 자동완성: 검색과 같은 discard에 동의어는 없다. search_as_you_type은 토큰이 같은 위치에 겹치면 색인에 실패한다
	//   "계룡산 트레일런" → 계룡, 산, 트레일런 (mixed로 넣으면 계룡산·계룡이 겹쳐 400 오류)
	private static final String AUTOCOMPLETE_ANALYZER = "race_autocomplete_analyzer";

	@Field(type = FieldType.Keyword)
	private String raceId;

	// 대회 연관검색어는 raceTitle.auto_complete로 찾는다
	//   "서울 하" 입력 → 대회 "2026 JTBC 서울마라톤" (종목 문서마다 같은 대회가 있으므로 raceId로 중복을 없앤다)
	@MultiField(
		mainField = @Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER),
		otherFields = @InnerField(suffix = "auto_complete", type = FieldType.Search_As_You_Type,
			analyzer = AUTOCOMPLETE_ANALYZER)
	)
	private String raceTitle;

	// "하프마라톤"으로 검색해도 종목명 "하프"가 맞는다 (동의어: 하프, 하프코스, 하프마라톤)
	@Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
	private String courseName;

	// 종목 연관검색어는 courseLabel.auto_complete로 찾는다 ("대회명 종목명")
	//   "서울 하" 입력 → 종목 "2026 JTBC 서울마라톤 하프", "계룡산 트" 입력 → 종목 "계룡산 트레일런 10K"
	//   한 요청에서 두 auto_complete 필드에 이름표(_name: race, course)를 붙여 찾고,
	//   결과의 matched_queries로 대회 연관검색어와 종목 연관검색어를 나눈다
	//   목록 검색어로는 찾지 않는다 (대회명과 겹쳐 점수가 두 번 붙는다)
	@MultiField(
		mainField = @Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER),
		otherFields = @InnerField(suffix = "auto_complete", type = FieldType.Search_As_You_Type,
			analyzer = AUTOCOMPLETE_ANALYZER)
	)
	private String courseLabel;

	// 대회 소개. 목록 검색어로만 찾는다. HTML이 들어와도 분석기가 태그를 지운다 (html_strip)
	@Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
	private String description;

	// 지역·장소는 검색창으로 찾고(text), 지역 필터는 keyword 하위 필드로 거른다
	//   검색 "충남" → regionSido "충청남도"와 맞는다 (동의어)
	//   필터는 글자가 똑같아야 한다: regionSido.keyword = "충청남도", regionSigungu.keyword = "공주시"
	//   "서울특별시"는 nori가 서울, 특별시로 쪼개서 "서울"만으로도 맞는다
	@MultiField(
		mainField = @Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER),
		otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
	)
	private String location;

	@MultiField(
		mainField = @Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER),
		otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
	)
	private String regionSido;

	@MultiField(
		mainField = @Field(type = FieldType.Text, analyzer = INDEX_ANALYZER, searchAnalyzer = SEARCH_ANALYZER),
		otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
	)
	private String regionSigungu;

	@Field(type = FieldType.Keyword)
	private CourseType courseType;

	@Field(type = FieldType.Keyword)
	private CourseSurface surface;

	@Field(type = FieldType.Keyword)
	private RaceSaleStatus saleStatus;

	@Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
	private LocalDateTime raceDate;

	@Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
	private LocalDateTime applicationCloseAt;

	// 판매 시작일 (이날 0시부터 판매 중)
	@Field(type = FieldType.Date, format = DateFormat.date)
	private LocalDate saleOpenDate;

	// 대회 등록 일시 (최신 등록순). 문서의 createdAt은 종목 등록 일시라 따로 둔다
	@Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
	private LocalDateTime raceCreatedAt;

	@Field(type = FieldType.Integer)
	private long price;

	@Field(type = FieldType.Boolean)
	private boolean kitIncluded;

	// 거리 필터는 판매자가 입력한 값(소수 둘째 자리까지)과 똑같은 종목만 거른다 (10 → 10.00 종목)
	@Field(type = FieldType.Double)
	private double distanceKm;

	// 필요 페이스(분/km, 소수점 버림). '페이스 n분대' 검색에 쓴다
	//   제한 시간 360분 ÷ 42.195km = 8.53 → 8 (8분대)
	@Field(type = FieldType.Integer)
	private int paceMinutes;

	@Field(type = FieldType.Integer)
	private int minAge;

	@Field(type = FieldType.Integer)
	private Integer maxAge;

	private RaceCourseDocument(RaceCourse course, Race race) {
		super(String.valueOf(course.getId()), course.getCreateDate());
		this.raceId = String.valueOf(race.getId());
		this.raceTitle = race.getTitle();
		this.courseName = course.getName();
		this.courseLabel = race.getTitle() + " " + course.getName();
		this.description = race.getDescription();
		this.location = race.getLocation();
		this.regionSido = race.getRegionSido();
		this.regionSigungu = race.getRegionSigungu();
		this.courseType = course.getCourseType();
		this.surface = course.getSurface();
		this.saleStatus = race.getSaleStatus();
		this.raceDate = race.getRaceDate();
		this.applicationCloseAt = race.getApplicationCloseAt();
		this.saleOpenDate = race.getSaleOpenDate();
		this.raceCreatedAt = race.getCreateDate();
		this.price = course.getPrice();
		this.kitIncluded = course.isKitIncluded();
		this.distanceKm = course.getDistanceKm().doubleValue();
		this.paceMinutes = BigDecimal.valueOf(course.getTimeLimitMinutes())
			.divide(course.getDistanceKm(), 0, RoundingMode.FLOOR)
			.intValue();
		this.minAge = course.getMinAge();
		this.maxAge = course.getMaxAge();
	}

	// 저장된 종목으로 만든다 (종목·대회 모두 ID가 있어야 한다)
	public static RaceCourseDocument from(RaceCourse course) {
		return new RaceCourseDocument(course, course.getRace());
	}
}
