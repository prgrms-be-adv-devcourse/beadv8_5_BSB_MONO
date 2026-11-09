package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

/**
 * 계정 연결 표의 내용 (BE-032 결정 3). 로그인한 회원이 받아 2분 안에 한 번만 쓴다
 * messageConsent가 true면 연결이 아니라 카카오 메시지 추가 동의 표다 (kakao-crew-invite 결정 2)
 */
public record LinkTicket(long memberId, SocialProvider provider, boolean messageConsent) {
	public LinkTicket(long memberId, SocialProvider provider) {
		this(memberId, provider, false);
	}
}
