package com.bukang.member.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.SocialAccount;
import com.bukang.member.domain.SocialProvider;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	boolean existsByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	// 소셜 로그인: 이 소셜 계정에 연결된 탈퇴하지 않은 회원의 ID (BE-036)
	// 탈퇴하면 연결 행도 지우지만(BE-034), 상태로 한 번 더 막는다
	@Query("select s.member.id from SocialAccount s"
		+ " where s.provider = :provider and s.providerUserId = :providerUserId and s.member.status <> :withdrawn")
	Optional<Long> findMemberIdNotIn(
		@Param("provider") SocialProvider provider,
		@Param("providerUserId") String providerUserId,
		@Param("withdrawn") MemberStatus withdrawn
	);

	default Optional<Long> findActiveMemberId(SocialProvider provider, String providerUserId) {
		return findMemberIdNotIn(provider, providerUserId, MemberStatus.WITHDRAWN);
	}
}
