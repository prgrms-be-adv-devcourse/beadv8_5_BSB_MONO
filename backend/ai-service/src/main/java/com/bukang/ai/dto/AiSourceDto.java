package com.bukang.ai.dto;

import java.time.LocalDateTime;

import com.bukang.ai.domain.AiSource;

// 답의 출처 한 줄. asOf(조회 시각)가 null이면 조회에 실패한 것이라 화면은 버튼만 보여 준다. 버튼이 없는 화면은 buttonLabel·path가 null
public record AiSourceDto(String screen, LocalDateTime asOf, String buttonLabel, String path) {
	public static AiSourceDto of(AiSource source, LocalDateTime asOf) {
		return new AiSourceDto(source.getScreen(), asOf, source.getButtonLabel(), source.getPath());
	}
}
