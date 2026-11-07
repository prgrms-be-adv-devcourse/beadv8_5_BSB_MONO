package com.bukang.member.out.kakao;

/**
 * 카카오 친구 한 명. 저장하지 않고 요청마다 받아 쓴다 (카카오 정책: 제공 목적 밖 저장 금지)
 *
 * @param id 카카오 회원번호. 크루런 소셜 연결의 provider_user_id와 같은 값이다
 * @param uuid 메시지를 보낼 때 쓰는 값
 * @param nickname 카카오톡 프로필 닉네임. 없으면 null
 * @param profileImageUrl 카카오톡 프로필 썸네일. 없으면 null
 */
public record KakaoFriend(String id, String uuid, String nickname, String profileImageUrl) {
	// 회원번호·uuid는 그 사람을 알아보는 값이라 로그에 남기지 않는다
	@Override
	public String toString() {
		return "KakaoFriend[nickname=" + nickname + "]";
	}
}
