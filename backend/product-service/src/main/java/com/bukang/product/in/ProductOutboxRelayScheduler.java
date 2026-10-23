package com.bukang.product.in;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 1초마다 아웃박스의 미발행 메시지를 Kafka로 보낸다 (fixedDelay: 이전 실행이 끝나고 1초 뒤에 다시 실행)
 * 메시지는 저장한 뒤 평균 1초 늦게 나가고, 쌓인 메시지가 많으면 더 늦어진다.
 * 테스트에서는 등록하지 않는다. 테스트는 ProductFacade.relayOutbox()를 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class ProductOutboxRelayScheduler {
	private final ProductFacade productFacade;

	@Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
	public void relay() {
		int sent = productFacade.relayOutbox();
		if (sent > 0) {
			log.debug("아웃박스 메시지를 보냈습니다. 건수={}", sent);
		}
	}
}
