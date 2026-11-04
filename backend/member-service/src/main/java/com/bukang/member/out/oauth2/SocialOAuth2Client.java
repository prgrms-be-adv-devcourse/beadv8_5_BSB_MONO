package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 카카오·구글과 주고받는 부분 (BE-031). 운영은 SpringSocialOAuth2Client가 Spring Security OAuth2 Client 부품으로 처리하고,
 * 테스트는 이 인터페이스를 가짜로 바꿔 키 없이 흐름을 확인한다
 */
public interface SocialOAuth2Client {
	// 키가 설정된 제공자인지. 설정되지 않은 제공자로 시작하면 실패 화면으로 보낸다
	boolean isConfigured(SocialProvider provider);

	// 인가 요청을 만든다. state·PKCE·nonce를 새로 만들고, 제공자 로그인 화면 주소와 저장할 값을 돌려준다
	// 서블릿 요청은 라이브러리 리졸버가 받는 형식이라 넘긴다 (콜백 주소는 설정의 절대 주소를 쓴다)
	AuthorizationStart begin(SocialProvider provider, HttpServletRequest request);

	// 콜백에서 받은 코드를 토큰으로 바꾸고 사람 정보를 받는다. state·PKCE·구글 ID 토큰을 검사한다
	// 실패하면 SocialLoginFailedException을 던진다
	SocialIdentity complete(StoredAuthorization authorization, String code, String state);

	/**
	 * @param authorizationUri 브라우저를 보낼 제공자 로그인 화면 주소 (state, code_challenge 포함)
	 * @param authorization Redis에 저장할 값. next·linkMemberId는 비어 있고 부르는 쪽이 채운다
	 */
	record AuthorizationStart(String authorizationUri, StoredAuthorization authorization) {
	}
}
