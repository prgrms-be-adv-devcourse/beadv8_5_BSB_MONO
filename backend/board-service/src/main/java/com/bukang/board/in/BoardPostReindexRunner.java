package com.bukang.board.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.bukang.board.app.BoardPostReindexUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 게시판이 다 뜬 뒤 뒤쪽 스레드에서 글 검색 인덱스 채우기를 시작한다 (BUK-179). 기동 시간을 늘리지 않는다
 * 매번 돌아도 같은 결과라 「이미 채웠나」 기록이 없다. test 프로파일은 board.search.reindex.enabled=false라 돌지 않는다
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "board.search.reindex", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BoardPostReindexRunner {
	private final BoardPostReindexUseCase boardPostReindexUseCase;

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		Thread thread = new Thread(boardPostReindexUseCase::run, "board-post-reindex");
		thread.setDaemon(true);
		thread.start();
	}
}
