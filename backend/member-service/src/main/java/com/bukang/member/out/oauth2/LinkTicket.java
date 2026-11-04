package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

/**
 * 계정 연결 표의 내용 (BE-032 결정 3). 로그인한 회원이 받아 2분 안에 한 번만 쓴다
 */
public record LinkTicket(long memberId, SocialProvider provider) {
}
