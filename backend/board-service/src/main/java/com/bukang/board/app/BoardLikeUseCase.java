package com.bukang.board.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.dto.LikeDto;
import com.bukang.board.out.BoardPostLikeRepository;
import com.bukang.board.out.BoardPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요 누르기(PUT)·취소(DELETE). 두 번 눌러도, 누른 적 없이 취소해도 결과가 같은 요청이라 오류 없이 현재 상태를 돌려준다
 * 좋아요 행과 좋아요 수는 한 트랜잭션이다. 수는 행을 넣거나 지우는 데 성공했을 때만 DB가 직접 더하고 뺀다
 */
@Service
@RequiredArgsConstructor
public class BoardLikeUseCase {
	private final BoardPostRepository boardPostRepository;
	private final BoardPostLikeRepository boardPostLikeRepository;
	private final BoardPostFinder boardPostFinder;
	private final BoardWriterGuard boardWriterGuard;

	@Transactional
	public LikeDto like(long memberId, long postId) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		boardPostFinder.findViewable(postId, memberId);
		// 이미 눌렀으면 0행이라 수를 올리지 않는다. 동시에 온 같은 요청은 유니크 제약 때문에 하나만 1행이 된다
		if (boardPostLikeRepository.insertIgnore(postId, memberId, LocalDateTime.now()) == 1) {
			boardPostRepository.increaseLikeCount(postId);
		}
		return new LikeDto(boardPostRepository.findLikeCount(postId), true);
	}

	@Transactional
	public LikeDto unlike(long memberId, long postId) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		boardPostFinder.findViewable(postId, memberId);
		if (boardPostLikeRepository.deleteLike(postId, memberId) == 1) {
			boardPostRepository.decreaseLikeCount(postId);
		}
		return new LikeDto(boardPostRepository.findLikeCount(postId), false);
	}
}
