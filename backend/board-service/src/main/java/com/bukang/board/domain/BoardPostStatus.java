package com.bukang.board.domain;

/**
 * 글 상태. 지우기는 행을 지우지 않고 DELETED로 바꾼다 (소프트 삭제)
 * HIDDEN은 신고가 쌓여 자동으로 숨긴 상태다 . 쓴 사람에게만 보인다
 */
public enum BoardPostStatus {
	VISIBLE,
	HIDDEN,
	DELETED
}
