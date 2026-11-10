package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.PartyCloseUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매시 정각에 대회 다음 날이 된 결제 끝 파티를 종료한다 (PartyCloseUseCase, BUK-126)
 * 하루 한 번이면 되지만 그 시각에 서버가 꺼져 있어도 다음 시각에 처리하게 매시 돈다 (KitShipNoticeScheduler와 같은 방식)
 * 테스트에서는 등록하지 않는다. 테스트는 PartyCloseUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PartyCloseScheduler {
	private final PartyCloseUseCase partyCloseUseCase;

	@Scheduled(cron = "0 0 * * * *")
	public void closeFinishedRaces() {
		partyCloseUseCase.closeFinishedRaces(LocalDateTime.now());
	}
}
