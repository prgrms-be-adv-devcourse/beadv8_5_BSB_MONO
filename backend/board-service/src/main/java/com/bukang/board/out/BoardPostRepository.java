package com.bukang.board.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardCategory;
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

	// 목록: 보이는 글만. 최신순은 글 ID 순서가 곧 쓴 순서라 ID로 이어 부른다 (새 글이 올라와도 다음 쪽이 겹치지 않는다)
	// pattern은 LIKE 패턴이고 이스케이프 문자는 !다
	@Query("select p from BoardPost p where p.status = com.bukang.board.domain.BoardPostStatus.VISIBLE "
		+ "and p.category in :categories and (p.title like :pattern escape '!' or p.content like :pattern escape '!') "
		+ "and p.id < :beforeId order by p.id desc")
	List<BoardPost> findLatest(@Param("categories") Collection<BoardCategory> categories,
		@Param("pattern") String pattern, @Param("beforeId") long beforeId, Pageable pageable);

	// 인기순: 최근 since 이후에 쓴 글을 (좋아요 수, 글 ID) 내림차순으로 이어 부른다
	// shortcut: 쪽 사이에 좋아요 수가 바뀌면 글이 한 번 빠지거나 겹칠 수 있다. 정확히 막으려면 순위 스냅샷이 필요하다
	@Query("select p from BoardPost p where p.status = com.bukang.board.domain.BoardPostStatus.VISIBLE "
		+ "and p.category in :categories and (p.title like :pattern escape '!' or p.content like :pattern escape '!') "
		+ "and p.createDate >= :since "
		+ "and (p.likeCount < :likes or (p.likeCount = :likes and p.id < :beforeId)) "
		+ "order by p.likeCount desc, p.id desc")
	List<BoardPost> findPopular(@Param("categories") Collection<BoardCategory> categories,
		@Param("pattern") String pattern, @Param("since") LocalDateTime since,
		@Param("likes") long likes, @Param("beforeId") long beforeId, Pageable pageable);

	// 같은 글에 동시에 들어온 신고를 한 줄로 세운다. 신고 수를 센 뒤 숨기기까지 다른 신고가 끼어들지 못한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from BoardPost p where p.id = :postId")
	Optional<BoardPost> findLockedById(@Param("postId") long postId);

	// 관리자 목록: 숨겨진 글과 관리자가 처리한 글
	@Query("select p from BoardPost p where p.status = com.bukang.board.domain.BoardPostStatus.HIDDEN "
		+ "or p.handledAction is not null")
	List<BoardPost> findReported();
}
