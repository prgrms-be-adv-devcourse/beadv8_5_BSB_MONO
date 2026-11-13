package com.bukang.product.out;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;

/**
 * 대회 검색 문서(race_courses)로 공개 목록과 연관검색어, AI 추천(벡터 KNN, BUK-162)을 찾는다.
 * 문서는 종목 1개 = 1개라서, 목록은 raceId로 묶어(collapse) 대회 하나에 한 줄만 돌려준다.
 * 화면에 보일 값(남은 자리 등)은 찾은 대회 ID로 DB에서 읽는다 (RaceSearchUseCase).
 */
@Repository
@RequiredArgsConstructor
public class RaceSearchRepository {
	// 공개 목록에 나오는 판매 상태 (판매 종료·대회 취소는 뺀다)
	// 판매 중지도 넣는다. 이미 산 사람이 대회를 찾을 수 있어야 하고, 판매자가 다시 열 수 있다 (11/08 결정)
	//   예매는 자리 확보·대기열이 판매 중지를 보고 막는다
	private static final List<FieldValue> PUBLIC_STATUSES = List.of(
		FieldValue.of(RaceSaleStatus.SCHEDULED.name()), FieldValue.of(RaceSaleStatus.ON_SALE.name()),
		FieldValue.of(RaceSaleStatus.STOPPED.name()));
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
	// 대회별 문서 수를 세는 집계 이름과, 그 안에서 벡터가 있는 문서만 세는 집계 이름 (검색 문서 맞추기, BUK-162)
	private static final String DOCUMENTS_BY_RACE = "documentsByRace";
	private static final String WITH_EMBEDDING = "withEmbedding";
	// 한 번에 세는 최대 대회 수. 문서가 있는 대회가 이보다 많아지면 나눠 세도록 바꾼다
	private static final int MAX_RACE_BUCKETS = 10_000;
	// AI 추천에 나오는 판매 상태 (BUK-162). 목록(PUBLIC_STATUSES)과 달리 판매 중지는 뺀다
	//   목록은 이미 산 사람이 찾아야 해서 보여 주지만, 추천은 지금 살 수 있는 대회만 권한다
	private static final List<FieldValue> RECOMMEND_STATUSES = List.of(
		FieldValue.of(RaceSaleStatus.SCHEDULED.name()), FieldValue.of(RaceSaleStatus.ON_SALE.name()));
	// 종목 문서는 대회마다 여러 개고 벡터가 같다. KNN은 문서 단위로 찾으므로, 대회로 묶은 뒤에도 size개가 남게 넉넉히 찾는다
	private static final int KNN_DOCUMENTS_PER_RACE = 5;
	// KNN이 후보로 살펴볼 최소 문서 수 (많을수록 정확하고 느리다)
	private static final int KNN_MIN_CANDIDATES = 100;
	// 이 값보다 덜 비슷한 대회는 추천하지 않는다 (코사인 유사도, -1~1). 관계없는 대회가 억지로 채워지지 않게 한다
	// 모델마다 값의 범위가 달라(Ollama nomic-embed-text, OpenAI text-embedding-3-small) 실제 데이터로 보며 고친다
	private static final float MIN_SIMILARITY = 0.5f;

	private final ElasticsearchOperations elasticsearchOperations;
	// ES 9.x는 검색 결과의 _source에서 벡터를 뺀다. 벡터를 받으려면 요청에 exclude_vectors=false를 넣어야 하는데
	// Spring Data의 검색 요청에는 이 옵션이 없어 ES 클라이언트를 직접 쓴다 (findEmbedding)
	private final ElasticsearchClient elasticsearchClient;

	// 찾은 대회(정렬 순서대로)와 조건에 맞는 전체 대회 수
	public record RaceSearchResult(List<MatchedRace> races, long totalRaces) {
	}

	// 대회 하나의 검색 문서 수와, 그중 AI 추천 벡터가 있는 문서 수 (검색 문서 맞추기, BUK-162)
	public record RaceDocumentCount(long documents, long withEmbedding) {
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

	// 대회 ID(문자열)별 검색 문서 수와 벡터가 있는 문서 수를 한 번에 센다 (문서 본문은 받지 않는다)
	// 문서가 하나도 없는 대회는 결과에 없다
	public Map<String, RaceDocumentCount> countDocumentsByRace() {
		NativeQuery query = NativeQuery.builder()
			.withQuery(Query.of(q -> q.matchAll(all -> all)))
			.withAggregation(DOCUMENTS_BY_RACE, Aggregation.of(aggregation -> aggregation
				.terms(terms -> terms.field("raceId").size(MAX_RACE_BUCKETS))
				.aggregations(WITH_EMBEDDING, Aggregation.of(sub -> sub
					.filter(filter -> filter.exists(exists -> exists.field(RaceCourseDocument.RACE_EMBEDDING)))))))
			.withMaxResults(0)
			.build();
		SearchHits<RaceCourseDocument> hits = elasticsearchOperations.search(query, RaceCourseDocument.class);
		Map<String, RaceDocumentCount> counts = new HashMap<>();
		if (hits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
			ElasticsearchAggregation byRace = aggregations.get(DOCUMENTS_BY_RACE);
			if (byRace != null) {
				byRace.aggregation().getAggregate().sterms().buckets().array().forEach(bucket ->
					counts.put(bucket.key().stringValue(), new RaceDocumentCount(bucket.docCount(),
						bucket.aggregations().get(WITH_EMBEDDING).filter().docCount())));
			}
		}
		return counts;
	}

	// 벡터가 비슷한 대회를 비슷한 순서로 size개까지 찾는다 (AI 추천, BUK-162)
	// 판매 예정·판매 중이고 접수 마감 전인 대회만 찾는다(KNN filter: 거른 뒤에 비슷한 것을 찾아 size개를 채운다)
	// excludeRaceId가 있으면 그 대회는 뺀다 (비슷한 대회에서 보고 있는 대회 빼기). 카드의 최저가는 대회에서 가장 싼 종목의 값
	public List<MatchedRace> recommend(float[] vector, Long excludeRaceId, LocalDateTime now, int size) {
		int nearestDocuments = size * KNN_DOCUMENTS_PER_RACE;
		List<Float> queryVector = new ArrayList<>(vector.length);
		for (float value : vector) {
			queryVector.add(value);
		}
		KnnSearch knn = KnnSearch.of(search -> search.field(RaceCourseDocument.RACE_EMBEDDING)
			.queryVector(queryVector)
			.k(nearestDocuments)
			.numCandidates(Math.max(nearestDocuments * 2, KNN_MIN_CANDIDATES))
			.similarity(MIN_SIMILARITY)
			.filter(recommendFilter(excludeRaceId, now)));
		NativeQuery query = NativeQuery.builder()
			.withKnnSearches(knn)
			.withFieldCollapse(FieldCollapse.of(collapse -> collapse.field("raceId")
				.innerHits(inner -> inner.name(CHEAPEST_COURSE).size(1).sort(fieldSort("price", SortOrder.Asc)))))
			.withPageable(PageRequest.of(0, size))
			.build();
		return elasticsearchOperations.search(query, RaceCourseDocument.class).getSearchHits().stream()
			.map(hit -> new MatchedRace(Long.parseLong(hit.getContent().getRaceId()), cheapestPrice(hit)))
			.toList();
	}

	// 대회의 AI 추천 벡터를 읽는다 (비슷한 대회 찾기, BUK-162). 종목 문서들의 벡터는 같으므로 하나만 읽는다
	// 문서가 없거나 아직 벡터가 없으면 빈 값 (임베딩 동기화가 곧 채운다)
	public Optional<float[]> findEmbedding(long raceId) {
		String index = elasticsearchOperations.getIndexCoordinatesFor(RaceCourseDocument.class).getIndexName();
		try {
			SearchResponse<EmbeddingSource> response = elasticsearchClient.search(search -> search.index(index)
				.size(1)
				.query(q -> q.bool(bool -> bool
					.filter(filter -> filter.term(term -> term.field("raceId").value(String.valueOf(raceId))))
					.filter(filter -> filter.exists(exists -> exists.field(RaceCourseDocument.RACE_EMBEDDING)))))
				.source(source -> source.filter(filter -> filter.includes(RaceCourseDocument.RACE_EMBEDDING)
					.excludeVectors(false))),
				EmbeddingSource.class);
			return response.hits().hits().stream()
				.map(Hit::source)
				.filter(Objects::nonNull)
				.map(EmbeddingSource::raceEmbedding)
				.filter(Objects::nonNull)
				.findFirst();
		} catch (IOException exception) {
			throw new UncheckedIOException("대회 AI 추천 벡터를 읽지 못했습니다. raceId=" + raceId, exception);
		}
	}

	// findEmbedding이 받는 _source (벡터 칸 하나)
	public record EmbeddingSource(float[] raceEmbedding) {
	}

	// AI 추천 대상: 판매 예정·판매 중이고 접수 마감 전인 대회. 빼야 할 대회가 있으면 뺀다
	private Query recommendFilter(Long excludeRaceId, LocalDateTime now) {
		String nowText = now.format(DATE_TIME);
		BoolQuery.Builder bool = new BoolQuery.Builder()
			.filter(filter -> filter.terms(terms -> terms.field("saleStatus")
				.terms(values -> values.value(RECOMMEND_STATUSES))))
			.filter(filter -> filter.range(range -> range.date(date -> date.field("applicationCloseAt")
				.gt(nowText))));
		if (excludeRaceId != null) {
			bool.mustNot(mustNot -> mustNot.term(term -> term.field("raceId").value(String.valueOf(excludeRaceId))));
		}
		return bool.build()._toQuery();
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

	// 판매 예정·판매 중·판매 중지이고 아직 접수 마감 전인 대회만 보여 준다
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
