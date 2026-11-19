package com.bukang.board.app;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.exception.BoardAdminForbiddenException;
import com.bukang.board.out.member.MemberAdminPort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 신고 관리 앞에서 부른 회원이 관리자인지 회원 서비스에 묻는다
 */
@Component
@RequiredArgsConstructor
public class BoardAdminGuard {
	private final MemberAdminPort memberAdminPort;

	public void requireAdmin(long memberId) {
		if (!memberAdminPort.isAdmin(memberId)) {
			throw new BoardAdminForbiddenException();
		}
	}
}
