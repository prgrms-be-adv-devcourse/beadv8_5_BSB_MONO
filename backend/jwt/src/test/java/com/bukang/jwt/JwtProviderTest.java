package com.bukang.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {
	private static final String SECRET = "jwt-test-secret-key-0000-1111-2222-3333";
	private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

	private final JwtProvider provider = providerAt(NOW, SECRET);

	@Test
	@DisplayName("발급한 Access Token은 검증을 통과한다")
	void issuedAccessTokenPassesVerification() {
		String token = provider.issueAccessToken(42L);

		JwtClaims claims = provider.parse(token, TokenType.ACCESS);

		assertThat(claims.memberId()).isEqualTo(42L);
		assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
		assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
	}

	@Test
	@DisplayName("발급한 Refresh Token은 검증을 통과한다")
	void issuedRefreshTokenPassesVerification() {
		String token = provider.issueRefreshToken(42L);

		JwtClaims claims = provider.parse(token, TokenType.REFRESH);

		assertThat(claims.memberId()).isEqualTo(42L);
		assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
	}

	@Test
	@DisplayName("만료된 토큰은 EXPIRED로 거절한다")
	void rejectsExpiredTokenAsExpired() {
		String token = provider.issueAccessToken(42L);
		JwtProvider later = providerAt(NOW.plus(Duration.ofMinutes(31)), SECRET);

		assertThatThrownBy(() -> later.parse(token, TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class)
			.extracting("reason").isEqualTo(InvalidTokenException.Reason.EXPIRED);
	}

	@Test
	@DisplayName("서명 키가 다른 토큰은 INVALID로 거절한다")
	void rejectsTokenSignedWithOtherKey() {
		String token = providerAt(NOW, "another-secret-key-4444-5555-6666-7777").issueAccessToken(42L);

		assertThatThrownBy(() -> provider.parse(token, TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class)
			.extracting("reason").isEqualTo(InvalidTokenException.Reason.INVALID);
	}

	@Test
	@DisplayName("변조된 토큰은 INVALID로 거절한다")
	void rejectsTamperedToken() {
		String token = provider.issueAccessToken(42L);
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

		assertThatThrownBy(() -> provider.parse(tampered, TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class)
			.extracting("reason").isEqualTo(InvalidTokenException.Reason.INVALID);
	}

	@Test
	@DisplayName("Refresh Token을 Access Token으로 쓰면 거절한다")
	void rejectsRefreshTokenUsedAsAccessToken() {
		String refreshToken = provider.issueRefreshToken(42L);

		assertThatThrownBy(() -> provider.parse(refreshToken, TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class)
			.extracting("reason").isEqualTo(InvalidTokenException.Reason.INVALID);
	}

	@Test
	@DisplayName("형식이 아닌 문자열과 빈 값은 INVALID로 거절한다")
	void rejectsMalformedOrEmptyToken() {
		assertThatThrownBy(() -> provider.parse("not-a-jwt", TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parse("", TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> provider.parse(null, TokenType.ACCESS))
			.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	@DisplayName("서명 키가 32바이트보다 짧으면 생성할 수 없다")
	void rejectsSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> providerAt(NOW, "short-secret"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("32바이트");
	}

	private static JwtProvider providerAt(Instant now, String secret) {
		JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14));
		return new JwtProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
	}
}
