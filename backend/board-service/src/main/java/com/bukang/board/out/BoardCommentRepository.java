package com.bukang.board.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardComment;

import jakarta.persistence.LockModeType;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
	// 글의 댓글·답글 전부 (지운 것 포함), 오래된 순
	List<BoardComment> findByPostIdOrderByIdAsc(long postId);

	// 같은 댓글에 동시에 들어온 신고를 한 줄로 세운다 (BoardPostRepository.findLockedById와 같은 이유)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from BoardComment c where c.id = :commentId")
	Optional<BoardComment> findLockedById(@Param("commentId") long commentId);

	// 관리자 목록: 숨겨진 댓글과 관리자가 처리한 댓글
	@Query("select c from BoardComment c where c.status = com.bukang.board.domain.BoardCommentStatus.HIDDEN "
		+ "or c.handledAction is not null")
	List<BoardComment> findReported();
}
