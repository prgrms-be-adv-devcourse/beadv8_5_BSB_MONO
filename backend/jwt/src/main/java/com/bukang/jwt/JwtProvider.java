package com.bukang.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급과 검증. 토큰의 subject에는 회원 ID를, type 클레임에는 토큰 종류를 넣는다.
 * 모든 토큰에 무작위 jti를 넣어, 같은 회원에게 같은 초에 발급해도 토큰이 서로 달라진다.
 * Refresh Token에는 같은 로그인에서 이어진 토큰 묶음을 가리키는 familyId(fid 클레임)를 넣는다.
 */
public class JwtProvider {
	private static final int MIN_SECRET_BYTES = 32;
	private static final String TYPE_CLAIM = "type";
	// 쿠키에 실려 매 재발급마다 오가므로 짧은 이름을 쓴다
	private static final String FAMILY_ID_CLAIM = "fid";

	private final SecretKey key;
	private final JwtParser parser;
	private final Duration accessTokenExpiration;
	private final Clock clock;

	public JwtProvider(JwtProperties properties, Clock clock) {
		this.key = toKey(properties.secret());
		this.accessTokenExpiration = properties.accessTokenExpiration();
		this.clock = clock;
		this.parser = Jwts.parser()
			.verifyWith(key)
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	public String issueAccessToken(long memberId) {
		return issue(memberId, TokenType.ACCESS, clock.instant().plus(accessTokenExpiration), null,
			UUID.randomUUID().toString());
	}

	/**
	 * 회전용 Refresh Token을 발급한다. jti와 발급 시각은 이 메서드가 정한다.
	 * 만료는 회전 정책(최근 사용 기준 연장, 로그인 기준 상한)을 아는 호출하는 쪽이 계산해 넘긴다.
	 *
	 * @param familyId 로그인할 때 만들고 회전해도 그대로 이어받는 묶음 ID
	 * @param expiresAt 만료 시각. 지금보다 뒤여야 한다
	 */
	public String issueRefreshToken(long memberId, String familyId, Instant expiresAt) {
		if (familyId == null || familyId.isBlank()) {
			throw new IllegalArgumentException("familyId는 비어 있을 수 없습니다");
		}
		if (!expiresAt.isAfter(clock.instant())) {
			throw new IllegalArgumentException("만료 시각은 지금보다 뒤여야 합니다: " + expiresAt);
		}
		return issue(memberId, TokenType.REFRESH, expiresAt, familyId, UUID.randomUUID().toString());
	}

	/**
	 * 이미 발급한 Refresh Token을 같은 jti·familyId·만료 시각으로 다시 서명한다.
	 * Redis에는 토큰 원문 대신 jti와 만료 시각만 남기므로, 유예(GRACE) 때 지금 토큰을 이 메서드로 되살린다.
	 */
	public String reissueRefreshToken(long memberId, String familyId, String jti, Instant expiresAt) {
		if (familyId == null || familyId.isBlank() || jti == null || jti.isBlank()) {
			throw new IllegalArgumentException("familyId와 jti는 비어 있을 수 없습니다");
		}
		if (!expiresAt.isAfter(clock.instant())) {
			throw new IllegalArgumentException("만료 시각은 지금보다 뒤여야 합니다: " + expiresAt);
		}
		return issue(memberId, TokenType.REFRESH, expiresAt, familyId, jti);
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
			return new JwtClaims(memberId, expectedType, claims.getExpiration().toInstant(),
				claims.getId(), claims.get(FAMILY_ID_CLAIM, String.class));
		} catch (NumberFormatException e) {
			throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, "회원 ID가 올바르지 않습니다", e);
		}
	}

	private String issue(long memberId, TokenType type, Instant expiresAt, String familyId, String jti) {
		JwtBuilder builder = Jwts.builder()
			.id(jti)
			.subject(String.valueOf(memberId))
			.claim(TYPE_CLAIM, type.name())
			.issuedAt(Date.from(clock.instant()))
			.expiration(Date.from(expiresAt));
		if (familyId != null) {
			builder.claim(FAMILY_ID_CLAIM, familyId);
		}
		return builder.signWith(key).compact();
	}

	private static SecretKey toKey(String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
				"jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다 (openssl rand -base64 32로 생성)");
		}
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
