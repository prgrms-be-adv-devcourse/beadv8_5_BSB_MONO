package com.bukang.member.crew.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 크루가 없거나 없앤 크루
public class CrewNotFoundException extends BusinessException {
	public CrewNotFoundException() {
		super(HttpStatus.NOT_FOUND, "CREW_NOT_FOUND", "존재하지 않는 크루입니다.");
	}
}
