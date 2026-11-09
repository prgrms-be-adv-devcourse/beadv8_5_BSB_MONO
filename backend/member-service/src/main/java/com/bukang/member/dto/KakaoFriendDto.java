package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카카오톡으로 초대를 보낼 수 있는 카카오 친구 (크루런 회원이고 아직 이 크루원이 아님)")
public record KakaoFriendDto(
	@Schema(description = "카카오톡 메시지를 보낼 때 쓰는 값", example = "abcdefg0001")
	String uuid,
	@Schema(description = "카카오톡 프로필 닉네임. 없으면 null", example = "이수민")
	String nickname,
	@Schema(description = "카카오톡 프로필 썸네일 주소. 없으면 null", example = "https://k.kakaocdn.net/dn/a.jpg")
	String profileImageUrl
) {
}
