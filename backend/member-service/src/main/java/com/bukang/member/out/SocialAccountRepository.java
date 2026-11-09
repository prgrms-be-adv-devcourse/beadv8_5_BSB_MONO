package com.bukang.member.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.SocialAccount;
import com.bukang.member.domain.SocialProvider;

import jakarta.persistence.LockModeType;

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

	// 카카오 메시지 토큰을 읽고 바꿀 때 그 회원의 카카오 연결 행을 잠근다. 갱신이 겹쳐 버려진 리프레시 토큰을 저장하지 않게 한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SocialAccount s where s.member.id = :memberId and s.provider = :provider")
	Optional<SocialAccount> findByMemberIdAndProviderForUpdate(
		@Param("memberId") long memberId,
		@Param("provider") SocialProvider provider
	);

	boolean existsByMemberIdAndProvider(long memberId, SocialProvider provider);

	// 카카오 친구 중 크루런 회원: 행은 [카카오 회원번호, 회원 ID]
	@Query("select s.providerUserId, s.member.id from SocialAccount s"
		+ " where s.provider = :provider and s.providerUserId in :providerUserIds and s.member.status <> :withdrawn")
	List<Object[]> findMemberIdsByProviderUserIds(
		@Param("provider") SocialProvider provider,
		@Param("providerUserIds") Collection<String> providerUserIds,
		@Param("withdrawn") MemberStatus withdrawn
	);
}
