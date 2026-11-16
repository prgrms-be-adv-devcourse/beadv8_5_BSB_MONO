package com.bukang.product.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.product.domain.RaceEmbeddingSyncEvent;
import com.bukang.product.out.RaceEmbeddingSyncEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대회의 AI 추천 벡터를 만들 일(임베딩 동기화 이벤트)을 저장한다 (BUK-162). 처리는 임베딩 동기화 스케줄러가 한다.
 * MANDATORY: 대회를 저장하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 대회 저장이 롤백돼도 이벤트가 남거나, 이벤트만 저장에 실패할 수 있기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RaceEmbeddingSyncAppendUseCase {
	private final RaceEmbeddingSyncEventRepository raceEmbeddingSyncEventRepository;

	// 대회 ID만 남긴다. 처리할 때 DB의 최신 대회로 임베딩할 글을 다시 만든다
	public void append(long raceId) {
		raceEmbeddingSyncEventRepository.save(RaceEmbeddingSyncEvent.pending(raceId));
	}
}
