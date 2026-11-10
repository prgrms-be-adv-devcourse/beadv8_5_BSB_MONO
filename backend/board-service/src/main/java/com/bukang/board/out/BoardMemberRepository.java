package com.bukang.board.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardMember;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {
	// 복사본이 없는 회원만 넣는다. 만들었으면 1, 이미 있으면 0 (BUK-161)
	// 먼저 SELECT로 확인하면 같은 회원을 동시에 넣는 이벤트와 둘 다 통과하므로, 기본 키 제약으로 한 번에 판단한다
	// 중복 키 예외를 내지 않아 트랜잭션이 롤백 전용으로 바뀌지 않는다. 판매자 이벤트 시각(seller_changed_at)은 비워 둬 이후 판매자 이벤트가 그대로 반영된다
	@Modifying
	@Query(value = "insert ignore into board_member "
		+ "(id, nickname, seller_approved, withdrawn, create_date, modify_date) "
		+ "values (:id, :nickname, :sellerApproved, :withdrawn, :modifyDate, :modifyDate)", nativeQuery = true)
	int insertIgnore(
		@Param("id") long id,
		@Param("nickname") String nickname,
		@Param("sellerApproved") boolean sellerApproved,
		@Param("withdrawn") boolean withdrawn,
		@Param("modifyDate") LocalDateTime modifyDate
	);
}
