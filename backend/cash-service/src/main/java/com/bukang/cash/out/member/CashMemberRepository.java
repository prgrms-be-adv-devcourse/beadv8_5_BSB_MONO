package com.bukang.cash.out.member;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.cash.domain.member.CashMember;

public interface CashMemberRepository extends JpaRepository<CashMember, Long> {
	// 저장된 수정 일시보다 늦은 메시지일 때만 바꾸고, 바꾼 행 수를 돌려준다
	// 비교와 쓰기를 한 문장에서 해서, 메시지 두 개가 동시에 처리돼도 옛 값이 새 값을 덮지 못한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update CashMember m set m.nickname = :nickname, m.modifyDate = :modifyDate "
		+ "where m.id = :id and m.modifyDate < :modifyDate")
	int updateIfNewer(
		@Param("id") long id,
		@Param("nickname") String nickname,
		@Param("modifyDate") LocalDateTime modifyDate
	);
}
