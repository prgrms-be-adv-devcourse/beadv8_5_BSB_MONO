package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.product.domain.Race;
import com.bukang.product.dto.RaceSummaryDto;
import com.bukang.product.out.RaceRepository;
import com.bukang.product.out.RaceSearchRepository;
import com.bukang.product.out.RaceSearchRepository.MatchedRace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 추천 (BUK-162). 연관검색어(글자가 같은 대회)를 돕는 기능으로, 뜻이 비슷한 대회를 벡터 KNN으로 찾는다.
 * - 검색어 추천: 검색어를 임베딩해 그 벡터와 비슷한 대회 ("밤에 강변 달리기" → 야간 한강 대회)
 * - 비슷한 대회: 보고 있는 대회의 벡터와 비슷한 대회 (그 대회는 뺀다)
 * 판매 예정·판매 중이고 접수 마감 전인 대회만 권한다 (판매 중지는 목록과 달리 뺀다, RaceSearchRepository.recommend).
 * 추천은 보조 기능이라 실패해도 오류로 돌려주지 않는다. 임베딩 API·ES가 실패하면 빈 목록을 돌려줘 화면이 AI 영역만 숨긴다.
 * 트랜잭션 밖에서 돈다 (ProductFacade). 임베딩 API를 기다리는 동안 DB 연결을 잡지 않게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceRecommendUseCase {
	// 한 번에 권하는 대회 수
	static final int RECOMMEND_SIZE = 6;
	// 검색어가 이보다 짧으면 뜻을 알기 어려워 추천하지 않는다 (예: "서")
	static final int MIN_KEYWORD_LENGTH = 2;

	private final RaceTextEmbedder raceTextEmbedder;
	private final RaceSearchRepository raceSearchRepository;
	private final RaceRepository raceRepository;

	// 검색어와 뜻이 비슷한 대회. 검색어가 너무 짧거나 추천하지 못하면 빈 목록
	public List<RaceSummaryDto> recommendByKeyword(String keyword) {
		String text = keyword.strip();
		if (text.length() < MIN_KEYWORD_LENGTH) {
			return List.of();
		}
		try {
			float[] vector = raceTextEmbedder.embed(text);
			return toSummaries(raceSearchRepository.recommend(vector, null, LocalDateTime.now(), RECOMMEND_SIZE));
		} catch (RuntimeException exception) {
			// 검색어는 로그에 남기지 않는다 (사용자가 입력한 글)
			log.warn("검색어로 AI 추천을 하지 못해 빈 목록을 돌려줍니다. 원인={}", exception.toString());
			return List.of();
		}
	}

	// 보고 있는 대회와 비슷한 대회 (그 대회는 뺀다). 대회의 벡터가 아직 없거나 추천하지 못하면 빈 목록
	// 벡터는 다시 임베딩하지 않고 검색 문서에 저장된 것을 쓴다 (임베딩 API를 부르지 않는다)
	public List<RaceSummaryDto> recommendSimilar(long raceId) {
		try {
			Optional<float[]> vector = raceSearchRepository.findEmbedding(raceId);
			if (vector.isEmpty()) {
				return List.of();
			}
			return toSummaries(raceSearchRepository.recommend(vector.get(), raceId, LocalDateTime.now(),
				RECOMMEND_SIZE));
		} catch (RuntimeException exception) {
			log.warn("비슷한 대회를 찾지 못해 빈 목록을 돌려줍니다. raceId={}, 원인={}", raceId, exception.toString());
			return List.of();
		}
	}

	// 찾은 순서(비슷한 순서)대로 카드에 보일 값을 DB에서 읽는다 (공개 목록과 같은 카드)
	// 찾은 뒤 지워진 대회는 뺀다
	private List<RaceSummaryDto> toSummaries(List<MatchedRace> matched) {
		if (matched.isEmpty()) {
			return List.of();
		}
		List<Long> raceIds = matched.stream().map(MatchedRace::raceId).toList();
		Map<Long, Race> racesById = raceRepository.findAllWithCoursesByIdIn(raceIds).stream()
			.collect(Collectors.toMap(Race::getId, Function.identity()));
		return matched.stream()
			.filter(race -> racesById.containsKey(race.raceId()))
			.map(race -> RaceSummaryDto.from(racesById.get(race.raceId()), race.minPrice()))
			.toList();
	}
}
