package com.bukang.board.domain.exception;

// 글 검색 인덱스에 넣거나 지우지 못했다. 던져서 부른 쪽(Kafka 리스너, 인덱스 채우기)이 다시 시도하거나 멈추게 한다
public class BoardSearchIndexException extends RuntimeException {
	public BoardSearchIndexException(String message, Throwable cause) {
		super(message, cause);
	}

	public BoardSearchIndexException(String message) {
		super(message);
	}
}
