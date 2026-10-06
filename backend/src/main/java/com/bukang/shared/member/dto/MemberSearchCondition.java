package com.bukang.shared.member.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberSearchCondition {
	private String username;
	private String nickname;
	private String email;
	private String phone;
}
