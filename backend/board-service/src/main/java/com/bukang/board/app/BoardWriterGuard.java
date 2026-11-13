package com.bukang.board.app;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.exception.BoardMemberWithdrawnException;
import com.bukang.board.out.BoardMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 모든 쓰기 요청(글·댓글·좋아요) 앞에서 탈퇴한 회원을 막는다.
 * 복사본이 아직 없는 회원은 막지 않는다 (가입 이벤트가 늦거나 배포 전 가입자라 복사본이 없을 수 있다)
 */
@Component
@RequiredArgsConstructor
public class BoardWriterGuard {
	private final BoardMemberRepository boardMemberRepository;

	public void checkNotWithdrawn(long memberId) {
		if (boardMemberRepository.findById(memberId).filter(BoardMember::isWithdrawn).isPresent()) {
			throw new BoardMemberWithdrawnException();
		}
	}
}
