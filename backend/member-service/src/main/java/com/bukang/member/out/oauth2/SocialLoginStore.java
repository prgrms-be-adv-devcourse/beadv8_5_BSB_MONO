package com.bukang.member.out.oauth2;

import java.util.Optional;

/**
 * 소셜 로그인 도중의 짧은 상태(인가 요청, 가입 대기, 연결 표)를 저장한다 (BE-031·032·033)
 * 운영은 Redis(RedisSocialLoginStore)에 두고, 테스트는 메모리 구현으로 바꿔 끼운다
 * take는 꺼내면서 지운다. 같은 값을 두 번 쓰지 못하게 해서, 새어 나간 state·연결 표를 다시 쓸 수 없다
 */
public interface SocialLoginStore {
	void saveAuthorization(String requestId, StoredAuthorization authorization);

	Optional<StoredAuthorization> takeAuthorization(String requestId);

	void savePendingSignup(String pendingId, PendingSignup pendingSignup);

	Optional<PendingSignup> findPendingSignup(String pendingId);

	void deletePendingSignup(String pendingId);

	void saveLinkTicket(String ticket, LinkTicket linkTicket);

	Optional<LinkTicket> takeLinkTicket(String ticket);
}
