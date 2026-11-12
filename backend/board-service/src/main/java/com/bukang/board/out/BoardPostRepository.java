package com.bukang.board.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardPost;

import jakarta.persistence.LockModeType;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {
	// 좋아요 수는 읽고 더해 저장하지 않고 DB가 직접 더한다. 읽고 저장하면 동시에 누른 두 명 중 하나가 사라진다
	// DB가 같은 글 행을 한 번에 하나씩만 고치므로 동시에 눌러도 모두 더해진다
	@Modifying
	@Query("update BoardPost p set p.likeCount = p.likeCount + 1 where p.id = :postId")
	int increaseLikeCount(@Param("postId") long postId);

	// 좋아요 행을 지운 경우에만 부른다. 0 밑으로 내려가지 않게 막는다
	@Modifying
	@Query("update BoardPost p set p.likeCount = p.likeCount - 1 where p.id = :postId and p.likeCount > 0")
	int decreaseLikeCount(@Param("postId") long postId);

	@Query("select p.likeCount from BoardPost p where p.id = :postId")
	long findLikeCount(@Param("postId") long postId);

	// 같은 글에 동시에 들어온 신고를 한 줄로 세운다. 신고 수를 센 뒤 숨기기까지 다른 신고가 끼어들지 못한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from BoardPost p where p.id = :postId")
	Optional<BoardPost> findLockedById(@Param("postId") long postId);

	// 관리자 목록: 숨겨진 글과 관리자가 처리한 글
	@Query("select p from BoardPost p where p.status = com.bukang.board.domain.BoardPostStatus.HIDDEN "
		+ "or p.handledAction is not null")
	List<BoardPost> findReported();
}
