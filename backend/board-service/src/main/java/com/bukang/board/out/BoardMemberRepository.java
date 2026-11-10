package com.bukang.board.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.board.domain.BoardMember;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {
	// 복사본이 없으면 넣고, 있어도 프로필을 아직 못 받은 행(modify_date가 비어 있음, 판매자 이벤트가 먼저 만든 행)이면
	// 닉네임·생성 시각·탈퇴 여부·수정 시각만 채운다. 판매자 칸(seller_approved, seller_changed_at)과 프로필을 이미 받은 행은 건드리지 않는다 (BUK-161)
	// 먼저 SELECT로 확인하면 같은 회원을 동시에 넣는 이벤트와 둘 다 통과하므로, 기본 키 제약으로 한 번에 판단한다. 중복 키 예외를 내지 않는다
	// MySQL은 SET을 왼쪽부터 적용하므로, 조건에 쓰는 modify_date를 맨 마지막에 바꾼다
	@Modifying
	@Query(value = "insert into board_member "
		+ "(id, nickname, seller_approved, withdrawn, create_date, modify_date) "
		+ "values (:id, :nickname, :sellerApproved, :withdrawn, :createDate, :modifyDate) "
		+ "on duplicate key update "
		+ "nickname = case when modify_date is null then :nickname else nickname end, "
		+ "create_date = case when modify_date is null then :createDate else create_date end, "
		+ "withdrawn = case when modify_date is null then :withdrawn else withdrawn end, "
		+ "modify_date = case when modify_date is null then :modifyDate else modify_date end",
		nativeQuery = true)
	void upsertProfile(
		@Param("id") long id,
		@Param("nickname") String nickname,
		@Param("sellerApproved") boolean sellerApproved,
		@Param("withdrawn") boolean withdrawn,
		@Param("createDate") LocalDateTime createDate,
		@Param("modifyDate") LocalDateTime modifyDate
	);
}
