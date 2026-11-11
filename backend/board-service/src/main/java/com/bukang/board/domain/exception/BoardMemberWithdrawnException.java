package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 탈퇴한 회원의 쓰기 요청이다. Access Token이 만료되기 전(최대 30분)에는 게이트웨이를 통과할 수 있다
public class BoardMemberWithdrawnException extends BusinessException {
	public BoardMemberWithdrawnException() {
		super(HttpStatus.FORBIDDEN, "BOARD_MEMBER_WITHDRAWN", "탈퇴한 회원은 글을 쓸 수 없습니다.");
	}
}
