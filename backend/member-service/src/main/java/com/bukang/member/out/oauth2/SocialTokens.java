package com.bukang.member.out.oauth2;

import java.time.Instant;
import java.util.Set;

/**
 * 카카오 코드 교환으로 받은 사용자 토큰 (kakao-crew-invite 결정 1). 메시지 추가 동의 콜백에서만 저장한다
 *
 * @param accessTokenExpiresAt 액세스 토큰이 끝나는 시각
 * @param refreshToken 없으면 null
 * @param scopes 카카오가 이 토큰에 준 동의 범위 (이미 동의한 범위 포함)
 */
public record SocialTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken, Set<String> scopes) {
	// 토큰 값은 로그에 찍혀도 나오지 않게 가린다
	@Override
	public String toString() {
		return "SocialTokens[accessTokenExpiresAt=" + accessTokenExpiresAt + ", scopes=" + scopes + "]";
	}
}
