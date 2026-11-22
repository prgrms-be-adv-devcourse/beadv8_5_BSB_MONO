package com.bukang.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 추천 기준값 (product.recommend.*, CRU-134)
 * 뜻이 비슷한 정도(코사인 유사도, -1~1)가 이 값보다 낮은 대회는 권하지 않는다. 관계없는 대회가 억지로 채워지지 않게 한다
 * 검색어는 짧은 글이라 대회 글끼리 견줄 때보다 점수가 0.1쯤 낮게 나와, 검색어와 비슷한 대회의 기준을 나눈다
 * 값의 범위는 임베딩 모델마다 달라(운영 OpenAI text-embedding-3-small, dev Ollama nomic-embed-text) 실제 데이터로 보며 고친다
 *
 * @param keywordMinSimilarity 검색어 AI 추천의 기준값
 *                             운영 측정(2026-11-22): 「한강」→ 한강 대회 0.38~0.39, 엉뚱한 대회는 대부분 0.35 아래
 * @param similarMinSimilarity 비슷한 대회(대회 상세)의 기준값
 *                             운영 측정(2026-11-22): 다른 마라톤끼리 0.45~0.6, 관계없는 대회끼리 0.41 이하
 */
@ConfigurationProperties("product.recommend")
public record RaceRecommendProperties(float keywordMinSimilarity, float similarMinSimilarity) {
}
