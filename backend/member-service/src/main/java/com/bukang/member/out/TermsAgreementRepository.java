package com.bukang.member.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {
	// 약관 종류까지 바로 읽는다 (트랜잭션 밖에서도 agreement.getTerms()를 쓸 수 있게)
	@EntityGraph(attributePaths = "terms")
	List<TermsAgreement> findByMemberId(long memberId);

	// 지금 동의 상태는 그 약관 종류의 마지막 기록이다 (BE-021). 변경은 회원 행 락으로 한 줄씩 쌓이므로 ID 순서가 기록 순서다
	// 결정 시각으로 고르지 않는 이유: 서버 시계가 뒤로 가면 늦게 쌓인 줄의 시각이 더 이를 수 있다
	Optional<TermsAgreement> findFirstByMemberIdAndTermsTypeOrderByIdDesc(long memberId, TermsType type);
}
