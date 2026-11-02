package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import com.bukang.product.domain.Race;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.domain.exception.RaceSearchRangeException;
import com.bukang.product.dto.CourseLabelSuggestionDto;
import com.bukang.product.dto.RacePageDto;
import com.bukang.product.dto.RaceSearchCondition;
import com.bukang.product.dto.RaceSuggestionDto;
import com.bukang.product.dto.RaceSummaryDto;
import com.bukang.product.dto.RaceTitleSuggestionDto;
import com.bukang.product.out.RaceRepository;
import com.bukang.product.out.RaceSearchRepository;
import com.bukang.product.out.RaceSearchRepository.MatchedRace;
import com.bukang.product.out.RaceSearchRepository.RaceSearchResult;

import lombok.RequiredArgsConstructor;

/**
 * 공개 대회 목록과 검색창 연관검색어. 찾기는 검색 문서(ES)로 하고, 목록에 보일 값은 DB에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class RaceSearchUseCase {
	// 연관검색어는 대회·종목을 각각 이만큼까지 보여 준다
	private static final int SUGGESTION_SIZE = 5;
	// 종목 문서가 대회마다 여러 개라 대회 중복을 빼고도 채울 수 있게 넉넉히 찾는다
	private static final int SUGGESTION_SEARCH_SIZE = 20;
	// ES는 앞에서부터 10,000번째 결과까지만 돌려준다 (index.max_result_window 기본값). 넘으면 ES가 거절해 500이 된다
	// 화면은 응답의 totalPages만큼만 페이지를 만들므로, 주소를 직접 고친 요청만 여기에 걸린다
	private static final int MAX_RESULT_WINDOW = 10_000;

	private final RaceSearchRepository raceSearchRepository;
	private final RaceRepository raceRepository;

	public RacePageDto search(RaceSearchCondition condition, int page, int size) {
		// page + 1을 int로 먼저 더하면 page가 int 최댓값일 때 음수로 넘치므로 long으로 바꾼 뒤 더한다
		if (((long)page + 1) * size > MAX_RESULT_WINDOW) {
			throw new RaceSearchRangeException("페이지 번호가 너무 큽니다.");
		}
		RaceSearchResult result = raceSearchRepository.search(condition, LocalDateTime.now(), page, size);
		// 남은 자리처럼 자주 바뀌는 값은 검색 문서에 없으므로 DB에서 읽는다. 순서는 검색 결과 순서를 따른다
		List<Long> raceIds = result.races().stream().map(MatchedRace::raceId).toList();
		Map<Long, Race> racesById = raceRepository.findAllById(raceIds).stream()
			.collect(Collectors.toMap(Race::getId, Function.identity()));
		// 카드의 최저가는 조건에 맞은 종목 중 가장 싼 값이다 (검색 문서에서 받은 값)
		List<RaceSummaryDto> races = result.races().stream()
			.filter(matched -> racesById.containsKey(matched.raceId()))
			.map(matched -> RaceSummaryDto.from(racesById.get(matched.raceId()), matched.minPrice()))
			.toList();
		return RacePageDto.of(races, page, size, result.totalRaces());
	}

	// 대회명으로 맞은 결과는 대회 연관검색어로, "대회명 종목명"으로 맞은 결과는 종목 연관검색어로 나눈다
	//   "서울 하" → 대회 "2026 JTBC 서울마라톤", 종목 "2026 JTBC 서울마라톤 하프"
	public RaceSuggestionDto suggest(String keyword) {
		List<SearchHit<RaceCourseDocument>> hits = raceSearchRepository.suggest(keyword, LocalDateTime.now(),
			SUGGESTION_SEARCH_SIZE);
		Map<Long, RaceTitleSuggestionDto> races = new LinkedHashMap<>();
		List<CourseLabelSuggestionDto> courses = new ArrayList<>();
		for (SearchHit<RaceCourseDocument> hit : hits) {
			RaceCourseDocument document = hit.getContent();
			long raceId = Long.parseLong(document.getRaceId());
			if (hit.getMatchedQueries().containsKey(RaceSearchRepository.RACE_SUGGESTION)
				&& races.size() < SUGGESTION_SIZE) {
				races.putIfAbsent(raceId, new RaceTitleSuggestionDto(raceId, document.getRaceTitle()));
			}
			if (hit.getMatchedQueries().containsKey(RaceSearchRepository.COURSE_SUGGESTION)
				&& courses.size() < SUGGESTION_SIZE) {
				courses.add(new CourseLabelSuggestionDto(Long.parseLong(document.getId()), raceId,
					document.getCourseLabel()));
			}
		}
		return new RaceSuggestionDto(List.copyOf(races.values()), courses);
	}
}
