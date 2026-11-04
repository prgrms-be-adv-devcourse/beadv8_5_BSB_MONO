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

	// 지금 동의 상태는 그 약관 종류의 마지막 기록이다 (BE-021). 결정 시각이 같으면 나중에 쌓인 줄이 이긴다
	Optional<TermsAgreement> findFirstByMemberIdAndTermsTypeOrderByDecidedAtDescIdDesc(long memberId, TermsType type);
}
