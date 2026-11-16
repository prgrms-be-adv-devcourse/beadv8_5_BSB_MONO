package com.bukang.product.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.product.app.ProductFacade;
import com.bukang.product.app.RaceSearchReconcileUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 10분마다 DB의 대회와 검색 문서를 견줘 어긋난 대회를 다시 맞추게 한다 (검색 문서 맞추기, BUK-162)
 * 앱이 뜨고 30초 뒤에 처음 돈다. 배포 전부터 있던 대회의 벡터 채우기(백필)도 이때 저절로 된다
 * fixedDelay: 이전 실행이 끝나고 10분 뒤에 다시 실행한다
 * 테스트에서는 등록하지 않는다. 테스트는 ProductFacade.reconcileRaceSearch()를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RaceSearchReconcileScheduler {
	private final ProductFacade productFacade;

	@Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
	public void reconcile() {
		RaceSearchReconcileUseCase.Result result = productFacade.reconcileRaceSearch();
		if (!result.isEmpty()) {
			log.info("검색 문서가 DB와 어긋난 대회를 다시 맞추게 했습니다. 문서 동기화={}, 임베딩 동기화={}",
				result.documentSyncAppended(), result.embeddingSyncAppended());
		}
	}
}
