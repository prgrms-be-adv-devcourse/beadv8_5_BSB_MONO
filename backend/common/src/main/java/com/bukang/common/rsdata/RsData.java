package com.bukang.common.rsdata;

import org.springframework.http.HttpStatus;

import com.bukang.common.standard.ResultType;

public record RsData<T>(int status, String message, T data) implements ResultType<T> {
	public static <T> RsData<T> of(HttpStatus status, String message, T data) {
		return new RsData<>(status.value(), message, data);
	}
}
