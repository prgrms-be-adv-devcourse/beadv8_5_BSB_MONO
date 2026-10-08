package com.bukang.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * jwt.* 설정값.
 *
 * @param secret HMAC 서명 키. UTF-8 기준 32바이트(256비트) 이상이어야 한다 (prod는 .env의 JWT_SECRET)
 * @param accessTokenExpiration Access Token 유효 기간
 * @param refreshTokenExpiration Refresh Token 유효 기간
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
	String secret,
	@DefaultValue("30m") Duration accessTokenExpiration,
	@DefaultValue("14d") Duration refreshTokenExpiration
) {
}
