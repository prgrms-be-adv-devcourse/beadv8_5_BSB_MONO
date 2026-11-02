package com.bukang.product.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.out.RaceDocumentSyncEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대회의 검색 문서를 DB에 맞출 일(동기화 이벤트)을 저장한다. 처리는 RaceDocumentSyncProcessUseCase가 한다.
 * MANDATORY: 대회를 저장하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 대회 저장이 롤백돼도 이벤트가 남거나, 이벤트만 저장에 실패할 수 있기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RaceDocumentSyncAppendUseCase {
	private final RaceDocumentSyncEventRepository raceDocumentSyncEventRepository;

	// 대회 ID만 남긴다. 처리할 때 DB의 최신 대회·종목을 읽으므로 바뀐 내용은 담지 않는다
	public void append(long raceId) {
		raceDocumentSyncEventRepository.save(RaceDocumentSyncEvent.pending(raceId));
	}
}
