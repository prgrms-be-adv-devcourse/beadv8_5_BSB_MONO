package com.bukang.member.app;

import java.util.List;

import com.bukang.member.domain.SocialProvider;

/**
 * 탈퇴·연결 해제로 소셜 연결 행을 지웠다 (BE-034). 커밋된 뒤 제공자 쪽 연결을 끊는 데 쓴다
 * 엔티티가 아니라 지운 시점의 값(제공자, 회원번호)을 담는다. 커밋 뒤에는 행이 이미 없다
 */
public record SocialAccountsUnlinkedEvent(List<Target> targets) {
	public record Target(SocialProvider provider, String providerUserId) {
	}
}
