package com.bukang.product.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 1초마다 미처리 임베딩 동기화 이벤트를 처리해 검색 문서의 AI 추천 벡터를 채운다 (BUK-162)
 * fixedDelay: 이전 실행이 끝나고 1초 뒤에 다시 실행한다 (임베딩 API가 느려도 겹쳐 돌지 않는다)
 * 대회를 등록한 뒤 문서 동기화(1초)가 문서를 만들고, 그 뒤 이 스케줄러가 벡터를 채운다. 먼저 돌면 다시 시도한다
 * 테스트에서는 등록하지 않는다. 테스트는 ProductFacade.syncRaceEmbeddings()를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RaceEmbeddingSyncScheduler {
	private final ProductFacade productFacade;

	@Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
	public void sync() {
		int synced = productFacade.syncRaceEmbeddings();
		if (synced > 0) {
			log.debug("대회 AI 추천 벡터를 채웠습니다. 대회 수={}", synced);
		}
	}
}
