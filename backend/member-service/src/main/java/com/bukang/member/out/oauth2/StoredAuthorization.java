package com.bukang.member.out.oauth2;

import java.util.Map;
import java.util.Set;

import com.bukang.member.domain.SocialProvider;

/**
 * 인가 요청 하나 (BE-031). 시작할 때 Redis에 10분 저장하고, 콜백에서 한 번 꺼내 쓰고 지운다
 *
 * @param state 콜백의 state와 같아야 진행한다
 * @param redirectUri 코드 교환 때 시작할 때와 같은 값을 보내야 한다
 * @param attributes PKCE 검증값(code_verifier), 구글 nonce 등 라이브러리가 콜백에서 다시 쓰는 값
 * @param next 끝난 뒤 돌아갈 앱 내부 경로 (검사를 마친 값)
 * @param linkMemberId 계정 연결이면 연결 표의 회원 ID, 로그인·가입이면 null (BE-032 결정 3)
 */
public record StoredAuthorization(
	SocialProvider provider,
	String state,
	String redirectUri,
	Set<String> scopes,
	Map<String, String> attributes,
	String next,
	Long linkMemberId
) {
	public boolean isLink() {
		return linkMemberId != null;
	}

	// state, PKCE 검증값은 로그에 남기지 않는다
	@Override
	public String toString() {
		return "StoredAuthorization[provider=" + provider + ", next=" + next + ", linkMemberId=" + linkMemberId + "]";
	}
}
