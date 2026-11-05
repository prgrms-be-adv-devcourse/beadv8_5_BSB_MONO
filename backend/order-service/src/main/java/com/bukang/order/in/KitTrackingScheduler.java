package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.KitTrackingUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배송 중인 키트의 배송 상태를 주기적으로 받아 온다 (BUK-55). 기본 30분(order.kit-tracking.interval)
 * Webhook 대신 주기적 조회를 쓴다: 알림에 상태가 없어 어차피 다시 조회해야 하고, 보낸 쪽을 확인할 수단이 없다 (11/03)
 * fixedDelay라 Delivery Tracker가 느려 한 번이 길어져도 실행이 겹치지 않는다
 * 테스트에서는 등록하지 않는다. 테스트는 KitTrackingUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class KitTrackingScheduler {
	private final KitTrackingUseCase kitTrackingUseCase;

	@Scheduled(fixedDelayString = "${order.kit-tracking.interval:PT30M}", initialDelay = 120_000)
	public void track() {
		KitTrackingUseCase.TrackResult result = kitTrackingUseCase.track(LocalDateTime.now());
		if (result.tracked() + result.failed() > 0) {
			log.info("키트 배송 조회: 조회 {}건(배송 완료 {}건), 실패 {}건", result.tracked(), result.delivered(), result.failed());
		}
	}
}
