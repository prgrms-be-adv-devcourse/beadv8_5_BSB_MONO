package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

/**
 * 소셜 가입 대기 (BE-033). 연결된 회원이 없는 소셜 계정을 Redis에 15분 둔다
 * 가입을 끝내면 지운다. 중복 닉네임 등으로 가입이 실패하면 남아 있어 다시 시도할 수 있다
 */
public record PendingSignup(SocialProvider provider, String providerUserId, String verifiedEmail, String name) {
	public static PendingSignup from(SocialIdentity identity) {
		return new PendingSignup(identity.provider(), identity.providerUserId(), identity.verifiedEmail(),
			identity.name());
	}

	@Override
	public String toString() {
		return "PendingSignup[provider=" + provider + ", providerUserId=" + providerUserId + "]";
	}
}
