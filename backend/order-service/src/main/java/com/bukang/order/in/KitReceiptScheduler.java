package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.KitDeliveryUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매시 30분에 배송 완료 7일이 지난 키트를 자동으로 수령 확인한다 (BUK-225, KitDeliveryUseCase.confirmOverdueReceipts)
 * 비어 있는 칸만 채우는 UPDATE 한 번이라 여러 서버가 함께 돌아도 한 번만 바뀐다
 * 테스트에서는 등록하지 않는다. 테스트는 KitDeliveryUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class KitReceiptScheduler {
	private final KitDeliveryUseCase kitDeliveryUseCase;

	@Scheduled(cron = "0 30 * * * *")
	public void confirmOverdueReceipts() {
		int confirmed = kitDeliveryUseCase.confirmOverdueReceipts(LocalDateTime.now());
		if (confirmed > 0) {
			log.info("배송 완료 7일이 지난 키트 {}건을 자동으로 수령 확인했습니다.", confirmed);
		}
	}
}
