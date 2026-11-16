package com.bukang.product.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 1초마다 미처리 동기화 이벤트를 처리해 대회 검색 문서를 DB에 맞춘다 (fixedDelay: 이전 실행이 끝나고 1초 뒤에 다시 실행)
 * 대회를 저장한 뒤 평균 1초 늦게 검색 결과에 반영된다.
 * 테스트에서는 등록하지 않는다. 테스트는 ProductFacade.syncRaceDocuments()를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RaceDocumentSyncScheduler {
	private final ProductFacade productFacade;

	@SchedulerLock(name = "product-race-document-sync", lockAtMostFor = "PT1M")
	@Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
	public void sync() {
		int synced = productFacade.syncRaceDocuments();
		if (synced > 0) {
			log.debug("대회 검색 문서를 DB에 맞췄습니다. 대회 수={}", synced);
		}
	}
}
