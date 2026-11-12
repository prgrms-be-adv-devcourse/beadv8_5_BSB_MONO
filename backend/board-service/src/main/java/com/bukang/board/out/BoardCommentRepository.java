package com.bukang.board.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardComment;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
	// 글의 댓글·답글 전부 (지운 것 포함), 오래된 순
	List<BoardComment> findByPostIdOrderByIdAsc(long postId);

	// 글마다 보이는 댓글·답글 수. [글 ID, 수] 목록이고 댓글이 없는 글은 나오지 않는다
	@Query("select c.postId, count(c) from BoardComment c where c.postId in :postIds "
		+ "and c.status = com.bukang.board.domain.BoardCommentStatus.VISIBLE group by c.postId")
	List<Object[]> countVisibleByPostIds(@Param("postIds") Collection<Long> postIds);
}
