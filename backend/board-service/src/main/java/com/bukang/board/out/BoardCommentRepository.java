package com.bukang.board.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.board.domain.BoardComment;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
	// 글의 댓글·답글 전부 (지운 것 포함), 오래된 순
	List<BoardComment> findByPostIdOrderByIdAsc(long postId);
}
