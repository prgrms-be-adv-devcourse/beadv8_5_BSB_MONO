package com.bukang.common.global.json;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 객체와 JSON 문자열 사이의 변환을 맡는다.
 * Spring Boot가 등록한 ObjectMapper 빈을 재사용해 spring.jackson.* 설정과 API 응답 JSON 형식을 그대로 따른다.
 */
@Component
@RequiredArgsConstructor
public class JsonConverter {
	private final ObjectMapper objectMapper;

	public String toJson(Object object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (JacksonException exception) {
			throw new IllegalStateException("JSON 변환에 실패했습니다.", exception);
		}
	}

	public <T> T fromJson(String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("JSON 파싱에 실패했습니다.", exception);
		}
	}
}
