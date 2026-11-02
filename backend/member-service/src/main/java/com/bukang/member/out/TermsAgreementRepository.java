package com.bukang.member.out;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.domain.TermsAgreement;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {
	// 약관 종류까지 바로 읽는다 (트랜잭션 밖에서도 agreement.getTerms()를 쓸 수 있게)
	@EntityGraph(attributePaths = "terms")
	List<TermsAgreement> findByMemberId(long memberId);
}
