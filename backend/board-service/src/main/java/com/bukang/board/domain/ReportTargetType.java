package com.bukang.board.domain;

import com.bukang.board.domain.exception.BoardReportNotFoundException;

/**
 * 신고 대상 종류. 관리자 주소에서는 posts, comments로 쓴다
 */
public enum ReportTargetType {
	POST,
	COMMENT;

	public static ReportTargetType fromPath(String path) {
		return switch (path) {
			case "posts" -> POST;
			case "comments" -> COMMENT;
			default -> throw new BoardReportNotFoundException();
		};
	}
}
