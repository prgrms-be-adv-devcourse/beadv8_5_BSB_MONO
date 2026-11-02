package com.bukang.product.out;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.SyncStatus;

public interface RaceDocumentSyncEventRepository extends JpaRepository<RaceDocumentSyncEvent, Long> {
	// 상태별 행을 만든 순서대로 limit건 읽는다 (만든 시각이 같으면 ID 순)
	List<RaceDocumentSyncEvent> findByStatusOrderByCreateDateAscIdAsc(SyncStatus status, Limit limit);
}
