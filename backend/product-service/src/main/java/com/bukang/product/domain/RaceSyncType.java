package com.bukang.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 검색 문서 동기화의 종류 (BUK-162). Slack 알림 글과 재처리 API 주소에 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum RaceSyncType {
	// 대회 검색 문서(race_courses) 동기화 (RaceDocumentSyncEvent)
	DOCUMENT("documents", "대회 검색 문서 동기화"),
	// AI 추천 벡터(raceEmbedding 칸) 동기화 (RaceEmbeddingSyncEvent)
	EMBEDDING("embeddings", "AI 추천 벡터 동기화");

	// 재처리 API 주소의 종류 부분 (/internal/v1/product/race-sync-failures/{path})
	private final String path;
	// 알림 글에 쓰는 이름
	private final String label;
}
