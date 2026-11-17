package com.bukang.member.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {
	// 약관 종류까지 바로 읽는다 (트랜잭션 밖에서도 agreement.getTerms()를 쓸 수 있게)
	@EntityGraph(attributePaths = "terms")
	List<TermsAgreement> findByMemberId(long memberId);

	// 지금 동의 상태는 그 약관 종류의 마지막 기록이다 (BE-021). 변경은 회원 행 락으로 한 줄씩 쌓이므로 ID 순서가 기록 순서다
	// 결정 시각으로 고르지 않는 이유: 서버 시계가 뒤로 가면 늦게 쌓인 줄의 시각이 더 이를 수 있다
	Optional<TermsAgreement> findFirstByMemberIdAndTermsTypeOrderByIdDesc(long memberId, TermsType type);

	// 마케팅 약관의 마지막 기록이 "동의"이고 동의 시각이 to 이전인, 탈퇴하지 않은 회원의 기록 (재동의 알림 후보, CRU-22)
	// shortcut: 2년 넘은 동의를 모두 읽어 자바에서 기념일을 고른다, 대상이 수만 건이 되면 기념일 조건을 SQL로 옮긴다
	@Query("select a from TermsAgreement a join fetch a.member m"
		+ " where a.terms.type = com.bukang.member.domain.TermsType.MARKETING"
		+ " and a.agreed = true and a.decidedAt <= :to"
		+ " and m.status <> com.bukang.member.domain.MemberStatus.WITHDRAWN"
		+ " and a.id = (select max(b.id) from TermsAgreement b where b.member = a.member"
		+ " and b.terms.type = com.bukang.member.domain.TermsType.MARKETING)")
	List<TermsAgreement> findMarketingAgreedUntil(@Param("to") LocalDateTime to);
}
