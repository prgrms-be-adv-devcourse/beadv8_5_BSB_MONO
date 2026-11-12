package com.bukang.board.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardPostLike;

public interface BoardPostLikeRepository extends JpaRepository<BoardPostLike, Long> {
	boolean existsByPostIdAndMemberId(long postId, long memberId);

	// 좋아요를 넣는다. 처음이면 1, (글, 회원)이 이미 있으면 0을 돌려준다
	// 먼저 SELECT로 확인하면 동시에 온 두 요청이 둘 다 통과하므로, DB의 유니크 제약으로 한 번에 판단한다
	// 중복 키 예외를 내지 않아 트랜잭션이 롤백 전용으로 바뀌지 않는다. 네이티브 쿼리라 생성·수정 시각도 직접 넣는다
	@Modifying
	@Query(value = "insert ignore into board_post_like (post_id, member_id, create_date, modify_date) "
		+ "values (:postId, :memberId, :now, :now)", nativeQuery = true)
	int insertIgnore(@Param("postId") long postId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);

	// 지운 행 수를 돌려준다. 1일 때만 좋아요 수를 뺀다
	@Modifying
	@Query("delete from BoardPostLike l where l.postId = :postId and l.memberId = :memberId")
	int deleteLike(@Param("postId") long postId, @Param("memberId") long memberId);
}
