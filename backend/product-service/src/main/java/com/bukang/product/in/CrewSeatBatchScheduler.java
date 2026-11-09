package com.bukang.product.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.product.app.CrewSeatBatchUseCase;
import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매시 정각(한국 시간)에 크루 자리를 정리한다 (BUK-110)
 * ① 결제 기한이 지난 결제 대기 크루 자리를 반환한다. 예: 13:12에 기한이 지나면 14:00 실행에서 반환
 * ② 크루 예매 마감일의 자정 실행에서 결제 대기 크루 자리를 모두 반환하고 남은 크루 자리를 개인 몫으로 옮긴다
 * 정각에 도는 이유: 개인 예매가 크루 예매 마감일의 자정에 열릴 수 있어, 그 순간 크루 몫을 바로 개인 몫으로 넘겨야 한다
 * 테스트에서는 등록하지 않는다. 테스트는 ProductFacade.runCrewSeatBatch에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CrewSeatBatchScheduler {
	private final ProductFacade productFacade;

	@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
	public void runCrewSeatBatch() {
		CrewSeatBatchUseCase.Result result = productFacade.runCrewSeatBatch(LocalDateTime.now());
		if (result.released() > 0 || result.closedCourses() > 0) {
			log.info("크루 자리를 정리했습니다. 반환한 결제 대기 자리 수={}, 개인 몫으로 옮긴 종목 수={}",
				result.released(), result.closedCourses());
		}
	}
}
