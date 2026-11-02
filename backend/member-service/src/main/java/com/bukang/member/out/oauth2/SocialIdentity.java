package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

/**
 * 제공자가 알려 준 사람 정보
 *
 * @param providerUserId 카카오 회원번호, 구글 sub
 * @param verifiedEmail 제공자가 확인했다고 한 이메일만 담는다. 확인되지 않았거나 동의하지 않았으면 null (BE-032 결정 2)
 * @param name 제공자가 준 실명. 없으면 null (가입 화면에 미리 채우기만 한다)
 */
public record SocialIdentity(SocialProvider provider, String providerUserId, String verifiedEmail, String name) {
	// 이메일·이름은 개인정보라 로그에 찍혀도 나오지 않게 가린다
	@Override
	public String toString() {
		return "SocialIdentity[provider=" + provider + ", providerUserId=" + providerUserId + "]";
	}
}
