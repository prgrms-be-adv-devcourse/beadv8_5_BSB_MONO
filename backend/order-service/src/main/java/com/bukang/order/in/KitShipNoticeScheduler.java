package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.order.app.KitShipNoticeUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매시 정각에 키트를 보낼 수 있게 된 대회의 판매자에게 알린다 (KitShipNoticeUseCase)
 * 환불 기한은 날짜라 하루 한 번이면 되지만, 그 시각에 서버가 꺼져 있어도 다음 시각에 보내게 매시 돈다. 대회마다 한 번만 보낸다
 * 시각은 발송(SellerKitFacade)과 같은 서버 시각을 써서, 알림을 받은 판매자가 바로 보낼 수 있게 한다
 * 테스트에서는 등록하지 않는다. 테스트는 KitShipNoticeUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class KitShipNoticeScheduler {
	private final KitShipNoticeUseCase kitShipNoticeUseCase;

	@SchedulerLock(name = "order-kit-ship-notice", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
	@Scheduled(cron = "0 0 * * * *")
	public void notifyShippable() {
		kitShipNoticeUseCase.notifyShippable(LocalDateTime.now());
	}
}
