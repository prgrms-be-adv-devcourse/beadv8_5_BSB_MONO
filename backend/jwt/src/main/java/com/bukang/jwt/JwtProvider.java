package com.bukang.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급과 검증. 토큰의 subject에는 회원 ID를, type 클레임에는 토큰 종류를 넣는다.
 */
public class JwtProvider {
	private static final int MIN_SECRET_BYTES = 32;
	private static final String TYPE_CLAIM = "type";

	private final SecretKey key;
	private final JwtParser parser;
	private final Duration accessTokenExpiration;
	private final Duration refreshTokenExpiration;
	private final Clock clock;

	public JwtProvider(JwtProperties properties, Clock clock) {
		this.key = toKey(properties.secret());
		this.accessTokenExpiration = properties.accessTokenExpiration();
		this.refreshTokenExpiration = properties.refreshTokenExpiration();
		this.clock = clock;
		this.parser = Jwts.parser()
			.verifyWith(key)
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	public String issueAccessToken(long memberId) {
		return issue(memberId, TokenType.ACCESS, accessTokenExpiration);
	}

	public String issueRefreshToken(long memberId) {
		return issue(memberId, TokenType.REFRESH, refreshTokenExpiration);
	}

	/**
	 * 서명, 만료, 토큰 종류를 검사하고 값을 꺼낸다.
	 *
	 * @throws InvalidTokenException 만료됐거나, 서명이 다르거나, 형식이 깨졌거나, 종류가 expectedType과 다를 때
	 */
	public JwtClaims parse(String token, TokenType expectedType) {
		Claims claims;
		try {
			claims = parser.parseSignedClaims(token).getPayload();
		} catch (ExpiredJwtException e) {
			throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, "만료된 토큰입니다", e);
		} catch (JwtException | IllegalArgumentException e) {
			// IllegalArgumentException: 토큰이 null이거나 빈 문자열일 때
			throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "유효하지 않은 토큰입니다", e);
		}

		if (!expectedType.name().equals(claims.get(TYPE_CLAIM, String.class))) {
			throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "토큰 종류가 다릅니다", null);
		}

		try {
			long memberId = Long.parseLong(claims.getSubject());
			return new JwtClaims(memberId, expectedType, claims.getExpiration().toInstant());
		} catch (NumberFormatException e) {
			throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "회원 ID가 올바르지 않습니다", e);
		}
	}

	private String issue(long memberId, TokenType type, Duration expiration) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(String.valueOf(memberId))
			.claim(TYPE_CLAIM, type.name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(expiration)))
			.signWith(key)
			.compact();
	}

	private static SecretKey toKey(String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
				"jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다 (openssl rand -base64 32로 생성)");
		}
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
