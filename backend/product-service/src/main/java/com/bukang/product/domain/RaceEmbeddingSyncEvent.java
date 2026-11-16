package com.bukang.product.domain;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * 대회 1개의 AI 추천 벡터(검색 문서의 raceEmbedding 칸)를 만들 일 1건 (대회 임베딩 동기화 이벤트, BUK-162).
 * 문서 동기화 이벤트(RaceDocumentSyncEvent)와 같은 방식이다: 대회를 저장하는 트랜잭션 안에서 함께 저장하고,
 * 같은 서비스의 스케줄러가 처리해 성공하면 COMPLETE, 실패하면 재시도 시각 뒤에 다시 처리한다.
 * 문서 동기화 이벤트와 표를 나눈 이유:
 * - 임베딩할 글(대회명·장소·지역·소개·종목 구성)이 바뀔 때만 남긴다. 판매 상태만 바뀌면 임베딩 API를 부르지 않는다
 * - 임베딩은 외부 API라 느리거나 실패할 수 있다. 문서 동기화와 따로 재시도해, 실패해도 목록 검색·연관검색어는 늦어지지 않는다
 * 처리는 DB의 최신 대회로 글을 다시 만들어 임베딩하므로, 행에는 대회 ID만 둔다.
 * 칸과 상태 변경은 문서 동기화 이벤트와 같아 BaseRaceSyncEvent에 있다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Table(
	name = "PRODUCT_RACE_EMBEDDING_SYNC_EVENT",
	// 미처리(PENDING) 행을 만든 순서대로 빠르게 찾는다 (스케줄러가 쓴다)
	indexes = @Index(name = "idx_race_embedding_sync_status_create", columnList = "status, create_date")
)
public class RaceEmbeddingSyncEvent extends BaseRaceSyncEvent {
	private RaceEmbeddingSyncEvent(long raceId) {
		super(raceId);
	}

	public static RaceEmbeddingSyncEvent pending(long raceId) {
		return new RaceEmbeddingSyncEvent(raceId);
	}
}
