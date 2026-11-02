package com.bukang.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bukang.member.domain.SocialProvider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MemberDto {
	private final long id;
	private final LocalDateTime createDate;
	private final LocalDateTime modifyDate;
	private final String nickname;
	private final String email;
	private final String phone;
	private final String name;
	private final LocalDate birthDate;
	// 계정 연결 화면이 연결 상태와 해제 가능 여부(마지막 로그인 방법인지)를 보여 주는 데 쓴다 (BE-032 결정 7)
	private final List<SocialProvider> socialProviders;
	private final boolean passwordSet;
}
