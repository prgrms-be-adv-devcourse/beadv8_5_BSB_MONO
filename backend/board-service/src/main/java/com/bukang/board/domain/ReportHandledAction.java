package com.bukang.board.domain;

import com.bukang.board.domain.exception.BoardReportNotFoundException;

/**
 * 관리자가 숨겨진 글·댓글에 한 처리. 관리자 주소에서는 restore, delete로 쓴다
 */
public enum ReportHandledAction {
	RESTORED,
	DELETED;

	public static ReportHandledAction fromPath(String path) {
		return switch (path) {
			case "restore" -> RESTORED;
			case "delete" -> DELETED;
			default -> throw new BoardReportNotFoundException();
		};
	}
}
