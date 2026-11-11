package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 글, 지운 글, 남의 숨겨진 글이다. 숨겨진 글이 있다는 사실이 드러나지 않도록 모두 404로 답한다
public class BoardPostNotFoundException extends BusinessException {
	public BoardPostNotFoundException() {
		super(HttpStatus.NOT_FOUND, "BOARD_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
	}
}
