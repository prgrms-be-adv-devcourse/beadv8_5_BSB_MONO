package com.bukang.product.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bukang.product.config.RaceRecommendProperties;
import com.bukang.product.out.RaceRepository;
import com.bukang.product.out.RaceSearchRepository;

/**
 * AI 추천이 검색어와 비슷한 대회에 각자의 기준값을 쓰는지 확인한다 (CRU-134).
 * 임베딩·검색 저장소는 가짜(@Mock)로 두고 RaceRecommendUseCase만 진짜로 돌린다. 점수로 실제 걸러지는지는 ES가 하므로 보지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RaceRecommendUseCaseTest {
	private static final float KEYWORD_MIN_SIMILARITY = 0.35f;
	private static final float SIMILAR_MIN_SIMILARITY = 0.45f;
	private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

	@Mock
	private RaceTextEmbedder raceTextEmbedder;
	@Mock
	private RaceSearchRepository raceSearchRepository;
	@Mock
	private RaceRepository raceRepository;

	private RaceRecommendUseCase raceRecommendUseCase;

	@BeforeEach
	void setUp() {
		RaceRecommendProperties properties = new RaceRecommendProperties(KEYWORD_MIN_SIMILARITY,
			SIMILAR_MIN_SIMILARITY);
		raceRecommendUseCase = new RaceRecommendUseCase(raceTextEmbedder, raceSearchRepository, raceRepository,
			properties);
	}

	@Test
	@DisplayName("검색어 AI 추천은 검색어용 기준값(0.35)으로 대회를 찾는다")
	void recommendByKeywordUsesKeywordMinSimilarity() {
		// given
		given(raceTextEmbedder.embed("한강")).willReturn(VECTOR);
		given(raceSearchRepository.recommend(any(), any(), any(Float.class), any(LocalDateTime.class), anyInt()))
			.willReturn(List.of());

		// when
		raceRecommendUseCase.recommendByKeyword("한강");

		// then
		then(raceSearchRepository).should()
			.recommend(eq(VECTOR), isNull(), eq(KEYWORD_MIN_SIMILARITY), any(LocalDateTime.class), anyInt());
	}

	@Test
	@DisplayName("비슷한 대회 추천은 비슷한 대회용 기준값(0.45)으로 대회를 찾는다")
	void recommendSimilarUsesSimilarMinSimilarity() {
		// given
		long raceId = 9L;
		given(raceSearchRepository.findEmbedding(raceId)).willReturn(Optional.of(VECTOR));
		given(raceSearchRepository.recommend(any(), any(), any(Float.class), any(LocalDateTime.class), anyInt()))
			.willReturn(List.of());

		// when
		raceRecommendUseCase.recommendSimilar(raceId);

		// then
		then(raceSearchRepository).should()
			.recommend(eq(VECTOR), eq(raceId), eq(SIMILAR_MIN_SIMILARITY), any(LocalDateTime.class), anyInt());
	}
}
