package com.bukang.jwt;

/**
 * 토큰 종류. 토큰 안에 기록해서, Refresh Token을 Access Token 자리에 쓰는 것을 막는다.
 */
public enum TokenType {
	ACCESS,
	REFRESH
}
