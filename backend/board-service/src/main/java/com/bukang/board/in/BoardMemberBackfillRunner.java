package com.bukang.board.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.bukang.board.app.BoardMemberBackfillUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 게시판이 다 뜬 뒤 뒤쪽 스레드에서 회원 복사본 채우기를 시작한다 (BUK-161). 기동 시간을 늘리지 않는다
 * test 프로파일은 board.member-backfill.enabled=false라 자동으로 돌지 않는다
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "board.member-backfill", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BoardMemberBackfillRunner {
	private final BoardMemberBackfillUseCase boardMemberBackfillUseCase;

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		Thread thread = new Thread(boardMemberBackfillUseCase::run, "board-member-backfill");
		thread.setDaemon(true);
		thread.start();
	}
}
