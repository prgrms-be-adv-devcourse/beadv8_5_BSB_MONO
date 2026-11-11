package com.bukang.board.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 말머리. 글을 쓸 때 고르지 않으면 자유다
 */
@Getter
@RequiredArgsConstructor
public enum BoardCategory {
	FREE("자유"),
	RACE_REVIEW("대회 후기"),
	QUESTION("질문"),
	RUN_TOGETHER("같이 뛰어요");

	// 화면에 쓰는 이름
	private final String label;
}
