package com.bukang.member.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MemberDto {
	private final int id;
	private final LocalDateTime createDate;
	private final LocalDateTime modifyDate;
	private final String username;
	private final String nickname;
	private final String email;
	private final String phone;
}
