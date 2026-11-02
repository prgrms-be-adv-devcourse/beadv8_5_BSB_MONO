package com.bukang.product.out;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.dto.RaceSearchCondition;
import com.bukang.product.dto.RaceSort;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import lombok.RequiredArgsConstructor;

/**
 * 대회 검색 문서(race_courses)로 공개 목록과 연관검색어를 찾는다.
 * 문서는 종목 1개 = 1개라서, 목록은 raceId로 묶어(collapse) 대회 하나에 한 줄만 돌려준다.
 * 화면에 보일 값(남은 자리 등)은 찾은 대회 ID로 DB에서 읽는다 (RaceSearchUseCase).
 */
@Repository
@RequiredArgsConstructor
public class RaceSearchRepository {
	// 공개 목록에 나오는 판매 상태 (판매 중지·판매 종료·대회 취소는 뺀다)
	private static final List<FieldValue> PUBLIC_STATUSES = List.of(
		FieldValue.of(RaceSaleStatus.SCHEDULED.name()), FieldValue.of(RaceSaleStatus.ON_SALE.name()));
	// 검색어로 찾는 칸과 가중치(^). 정확도순 점수는 가장 잘 맞은 칸 하나의 점수에 가중치를 곱한 값이다 (best_fields)
	//   대회명에서 맞으면 3배, 장소에서 맞으면 2배, 나머지는 1배
	private static final List<String> KEYWORD_FIELDS = List.of(
		"raceTitle^3", "location^2", "courseName", "regionSido", "regionSigungu", "description");
	// 오타 허용 범위. AUTO는 단어 길이로 정한다: 1~2글자는 허용 안 함, 3~5글자는 1글자, 6글자 이상은 2글자
	//   "트레일러" → 트레일런과 맞는다. "서울"처럼 두 글자 단어는 정확히 맞아야 한다
	//   동의어로 펼친 단어("충남" → 충청남도)에는 적용되지 않는다 (ES가 동의어에는 오타 허용을 하지 않는다)
	private static final String FUZZINESS = "AUTO";
	// 연관검색어 칸 (search_as_you_type은 하위 필드 _2gram·_3gram을 함께 찾는다)
	private static final List<String> RACE_SUGGEST_FIELDS = List.of(
		"raceTitle.auto_complete", "raceTitle.auto_complete._2gram", "raceTitle.auto_complete._3gram");
	private static final List<String> COURSE_SUGGEST_FIELDS = List.of(
		"courseLabel.auto_complete", "courseLabel.auto_complete._2gram", "courseLabel.auto_complete._3gram");
	// 연관검색어 결과에서 대회·종목을 나누는 이름표 (matched_queries)
	public static final String RACE_SUGGESTION = "race";
	public static final String COURSE_SUGGESTION = "course";
	// 묶은 결과의 대회 수를 세는 집계 이름
	private static final String RACE_COUNT = "raceCount";
	// 묶은 대회마다 조건에 맞은 종목 중 가장 싼 종목 1개를 함께 받는 이름 (inner_hits)
	private static final String CHEAPEST_COURSE = "cheapestCourse";
	// 문서의 날짜 형식 (date_hour_minute_second_millis)
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

	private final ElasticsearchOperations elasticsearchOperations;

	// 찾은 대회(정렬 순서대로)와 조건에 맞는 전체 대회 수
	public record RaceSearchResult(List<MatchedRace> races, long totalRaces) {
	}

	// 찾은 대회 ID와, 조건에 맞은 종목 중 가장 싼 참가비
	//   "하프"만 골랐으면 하프 종목들 중 가장 싼 값. 조건이 없으면 대회에서 가장 싼 종목의 값
	public record MatchedRace(long raceId, long minPrice) {
	}

	public RaceSearchResult search(RaceSearchCondition condition, LocalDateTime now, int page, int size) {
		NativeQuery query = NativeQuery.builder()
			.withQuery(searchQuery(condition, now))
			.withFieldCollapse(FieldCollapse.of(collapse -> collapse.field("raceId")
				.innerHits(inner -> inner.name(CHEAPEST_COURSE).size(1).sort(fieldSort("price", SortOrder.Asc)))))
			.withAggregation(RACE_COUNT, Aggregation.of(aggregation ->
				aggregation.cardinality(cardinality -> cardinality.field("raceId"))))
			.withSort(sortOptions(condition.getSort()))
			.withPageable(PageRequest.of(page, size))
			.build();
		SearchHits<RaceCourseDocument> hits = elasticsearchOperations.search(query, RaceCourseDocument.class);
		List<MatchedRace> races = hits.getSearchHits().stream()
			.map(hit -> new MatchedRace(Long.parseLong(hit.getContent().getRaceId()), cheapestPrice(hit)))
			.toList();
		return new RaceSearchResult(races, raceCount(hits));
	}

	// 입력한 글자로 시작하는 대회명·종목명을 찾는다. 결과마다 맞은 이름표(race / course)가 붙는다
	public List<SearchHit<RaceCourseDocument>> suggest(String keyword, LocalDateTime now, int size) {
		Query query = Query.of(q -> q.bool(bool -> publicFilter(bool, now)
			.should(should -> should.multiMatch(match -> match.query(keyword).type(TextQueryType.BoolPrefix)
				.fields(RACE_SUGGEST_FIELDS).queryName(RACE_SUGGESTION)))
			.should(should -> should.multiMatch(match -> match.query(keyword).type(TextQueryType.BoolPrefix)
				.fields(COURSE_SUGGEST_FIELDS).queryName(COURSE_SUGGESTION)))
			.minimumShouldMatch("1")));
		NativeQuery nativeQuery = NativeQuery.builder()
			.withQuery(query)
			.withPageable(PageRequest.of(0, size))
			.build();
		return elasticsearchOperations.search(nativeQuery, RaceCourseDocument.class).getSearchHits();
	}

	// 검색어는 점수를 매기는 must에, 나머지 조건은 점수 없이 거르기만 하는 filter에 넣는다
	private Query searchQuery(RaceSearchCondition condition, LocalDateTime now) {
		BoolQuery.Builder bool = publicFilter(new BoolQuery.Builder(), now);
		if (StringUtils.hasText(condition.getKeyword())) {
			// 단어는 하나만 맞아도 나온다(OR). "충남 트레일" → 둘 다 맞는 대회가 점수가 높다
			bool.must(must -> must.multiMatch(match -> match.query(condition.getKeyword()).fields(KEYWORD_FIELDS)
				.fuzziness(FUZZINESS)));
		}
		if (StringUtils.hasText(condition.getRegionSido())) {
			bool.filter(filter -> filter.term(term -> term.field("regionSido.keyword")
				.value(condition.getRegionSido())));
		}
		if (StringUtils.hasText(condition.getRegionSigungu())) {
			bool.filter(filter -> filter.term(term -> term.field("regionSigungu.keyword")
				.value(condition.getRegionSigungu())));
		}
		// 빈 값(courseType=)은 Spring이 null로 바꿔 주므로 조건 없음으로 보고 뺀다 (노면·정렬의 빈 값과 같게)
		List<FieldValue> courseTypes = condition.getCourseTypes() == null ? List.of()
			: condition.getCourseTypes().stream()
				.filter(Objects::nonNull)
				.map(courseType -> FieldValue.of(courseType.name()))
				.toList();
		if (!courseTypes.isEmpty()) {
			bool.filter(filter -> filter.terms(terms -> terms.field("courseType")
				.terms(values -> values.value(courseTypes))));
		}
		if (condition.getSurface() != null) {
			bool.filter(filter -> filter.term(term -> term.field("surface").value(condition.getSurface().name())));
		}
		if (condition.getDistanceKm() != null) {
			bool.filter(filter -> filter.term(term -> term.field("distanceKm")
				.value(condition.getDistanceKm().doubleValue())));
		}
		if (condition.getMinPrice() != null || condition.getMaxPrice() != null) {
			// 한쪽만 보내면 그쪽만 거른다 (null은 범위 끝을 두지 않는다)
			Double minPrice = condition.getMinPrice() == null ? null : condition.getMinPrice().doubleValue();
			Double maxPrice = condition.getMaxPrice() == null ? null : condition.getMaxPrice().doubleValue();
			bool.filter(filter -> filter.range(range -> range.number(number -> number.field("price")
				.gte(minPrice).lte(maxPrice))));
		}
		if (condition.getRaceDateFrom() != null) {
			String from = condition.getRaceDateFrom().atStartOfDay().format(DATE_TIME);
			bool.filter(filter -> filter.range(range -> range.date(date -> date.field("raceDate").gte(from))));
		}
		if (condition.getRaceDateTo() != null) {
			// 끝 날짜도 포함하도록 다음 날 0시 전까지
			String to = condition.getRaceDateTo().plusDays(1).atStartOfDay().format(DATE_TIME);
			bool.filter(filter -> filter.range(range -> range.date(date -> date.field("raceDate").lt(to))));
		}
		return bool.build()._toQuery();
	}

	// 판매 예정·판매 중이고 아직 접수 마감 전인 대회만 보여 준다
	private BoolQuery.Builder publicFilter(BoolQuery.Builder bool, LocalDateTime now) {
		String nowText = now.format(DATE_TIME);
		return bool
			.filter(filter -> filter.terms(terms -> terms.field("saleStatus")
				.terms(values -> values.value(PUBLIC_STATUSES))))
			.filter(filter -> filter.range(range -> range.date(date -> date.field("applicationCloseAt")
				.gt(nowText))));
	}

	// 같은 값이면 raceId로 순서를 고정한다 (페이지를 넘겨도 순서가 흔들리지 않게)
	// 묶기(collapse)는 조건에 맞은 종목 문서 중 정렬에서 가장 앞선 문서를 대회의 대표로 남긴다
	//   가격 낮은순이면 조건에 맞은 종목 중 가장 싼 종목이 대표가 되어, 그 값으로 대회 순서가 정해진다
	private List<SortOptions> sortOptions(RaceSort sort) {
		SortOptions raceIdSort = fieldSort("raceId", SortOrder.Asc);
		return switch (sort) {
			// 검색어가 없으면 점수가 모두 같아 마감 임박순이 된다
			case RELEVANCE -> List.of(SortOptions.of(option -> option.score(score -> score.order(SortOrder.Desc))),
				fieldSort("applicationCloseAt", SortOrder.Asc), raceIdSort);
			case DEADLINE -> List.of(fieldSort("applicationCloseAt", SortOrder.Asc), raceIdSort);
			case RACE_DATE -> List.of(fieldSort("raceDate", SortOrder.Asc), raceIdSort);
			// 종목 등록 일시(createdAt)가 아니라 대회 등록 일시로 정렬한다 (나중에 종목을 더해도 순서가 바뀌지 않게)
			case LATEST -> List.of(fieldSort("raceCreatedAt", SortOrder.Desc), raceIdSort);
			case PRICE -> List.of(fieldSort("price", SortOrder.Asc), raceIdSort);
		};
	}

	private SortOptions fieldSort(String field, SortOrder order) {
		return SortOptions.of(sort -> sort.field(fieldSort -> fieldSort.field(field).order(order)));
	}

	// 대회의 대표 문서와 함께 받은 '조건에 맞은 종목 중 가장 싼 종목'의 참가비
	private long cheapestPrice(SearchHit<RaceCourseDocument> hit) {
		SearchHits<?> cheapest = hit.getInnerHits(CHEAPEST_COURSE);
		if (cheapest == null || cheapest.isEmpty()) {
			return hit.getContent().getPrice();
		}
		return ((RaceCourseDocument)cheapest.getSearchHit(0).getContent()).getPrice();
	}

	private long raceCount(SearchHits<RaceCourseDocument> hits) {
		if (hits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
			ElasticsearchAggregation raceCount = aggregations.get(RACE_COUNT);
			if (raceCount != null) {
				return raceCount.aggregation().getAggregate().cardinality().value();
			}
		}
		return 0;
	}
}
