package com.bukang.member.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자 회원 찾기에 닉네임이 없거나 공백뿐이다 (CRU-156). 빈 값으로 전체 회원을 훑지 않게 막는다
public class AdminMemberSearchNicknameRequiredException extends BusinessException {
	public AdminMemberSearchNicknameRequiredException() {
		super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "찾을 닉네임을 입력해주세요.");
	}
}
