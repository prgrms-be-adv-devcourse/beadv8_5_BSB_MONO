package com.bukang.product.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.SyncStatus;

public interface RaceDocumentSyncEventRepository extends JpaRepository<RaceDocumentSyncEvent, Long> {
	// 상태별 행을 만든 순서대로 limit건 읽는다 (만든 시각이 같으면 ID 순)
	List<RaceDocumentSyncEvent> findByStatusOrderByCreateDateAscIdAsc(SyncStatus status, Limit limit);

	// 상태별 행을 최근 것부터 limit건 읽는다 (재처리 API의 실패 목록, BUK-162)
	List<RaceDocumentSyncEvent> findByStatusOrderByIdDesc(SyncStatus status, Limit limit);

	// 이 상태의 행이 있는 대회 ID (검색 문서 맞추기가 이미 처리 중이거나 실패한 대회를 건너뛸 때 쓴다, BUK-162)
	@Query("select distinct e.raceId from RaceDocumentSyncEvent e where e.status in :statuses")
	List<Long> findRaceIdsByStatusIn(@Param("statuses") Collection<SyncStatus> statuses);
}
