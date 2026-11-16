package com.bukang.member.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RevokedAccessTokenRepository;

// 같은 family일 때만 지우는 판단은 refresh-token-delete-family.lua가 한다 (RefreshTokenRepositoryTest)
class LogoutUseCaseTest {
	private static final JwtProperties PROPERTIES = new JwtProperties(
		"jwt-test-secret-key-0000-0000-0000-0000", Duration.ofMinutes(30), Duration.ofDays(14));
	private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
	private static final long MEMBER_ID = 7;

	private JwtProvider jwtProvider;
	private RefreshTokenRepository refreshTokenRepository;
	private RevokedAccessTokenRepository revokedAccessTokenRepository;
	private LogoutUseCase logoutUseCase;

	@BeforeEach
	void setUp() {
		jwtProvider = new JwtProvider(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
		refreshTokenRepository = mock(RefreshTokenRepository.class);
		revokedAccessTokenRepository = mock(RevokedAccessTokenRepository.class);
		logoutUseCase = new LogoutUseCase(jwtProvider, refreshTokenRepository, revokedAccessTokenRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("토큰의 familyId로 Redis 기록 삭제를 요청한다")
	void deletesByFamily() {
		String refreshToken = jwtProvider.issueRefreshToken(MEMBER_ID, "family-1", NOW.plus(Duration.ofDays(14)));

		logoutUseCase.logout(refreshToken, null);

		verify(refreshTokenRepository).deleteIfFamily(MEMBER_ID, "family-1");
	}

	@Test
	@DisplayName("토큰이 없거나 유효하지 않으면 아무것도 지우지 않고 끝난다")
	void ignoreMissingOrInvalidToken() {
		logoutUseCase.logout(null, null);
		logoutUseCase.logout(" ", null);
		logoutUseCase.logout("not-a-jwt", null);
		logoutUseCase.logout(jwtProvider.issueAccessToken(MEMBER_ID), null);

		verify(refreshTokenRepository, never()).deleteIfFamily(anyLong(), anyString());
	}

	@Test
	@DisplayName("요청의 Access Token jti를 남은 시간만큼만 막힌 토큰으로 남긴다")
	void revokesAccessTokenForRemainingTime() {
		String accessToken = jwtProvider.issueAccessToken(MEMBER_ID);
		String jti = jwtProvider.parse(accessToken, TokenType.ACCESS).jti();

		logoutUseCase.logout(null, "Bearer " + accessToken);

		verify(revokedAccessTokenRepository).revoke(jti, PROPERTIES.accessTokenExpiration());
	}

	@Test
	@DisplayName("Access Token과 Refresh Token을 함께 보내면 둘 다 처리한다")
	void revokesAccessAndDeletesRefreshTogether() {
		String accessToken = jwtProvider.issueAccessToken(MEMBER_ID);
		String refreshToken = jwtProvider.issueRefreshToken(MEMBER_ID, "family-1", NOW.plus(Duration.ofDays(14)));

		logoutUseCase.logout(refreshToken, "Bearer " + accessToken);

		verify(revokedAccessTokenRepository).revoke(anyString(), any(Duration.class));
		verify(refreshTokenRepository).deleteIfFamily(MEMBER_ID, "family-1");
	}

	@Test
	@DisplayName("Authorization 헤더가 없거나 위조·만료·종류가 다른 토큰이면 남기지 않고 넘어간다")
	void ignoresMissingOrInvalidAccessToken() {
		JwtProvider pastProvider = new JwtProvider(PROPERTIES,
			Clock.fixed(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC));
		JwtProvider otherProvider = new JwtProvider(new JwtProperties(
			"other-secret-key-0000-0000-0000-0000", Duration.ofMinutes(30), Duration.ofDays(14)),
			Clock.fixed(NOW, ZoneOffset.UTC));

		logoutUseCase.logout(null, null);
		logoutUseCase.logout(null, "");
		logoutUseCase.logout(null, "Bearer ");
		logoutUseCase.logout(null, "Basic abc");
		logoutUseCase.logout(null, "Bearer not-a-jwt");
		logoutUseCase.logout(null, "Bearer " + pastProvider.issueAccessToken(MEMBER_ID));
		logoutUseCase.logout(null, "Bearer " + otherProvider.issueAccessToken(MEMBER_ID));
		logoutUseCase.logout(null, "Bearer "
			+ jwtProvider.issueRefreshToken(MEMBER_ID, "family-1", NOW.plus(Duration.ofDays(14))));

		verify(revokedAccessTokenRepository, never()).revoke(anyString(), any(Duration.class));
	}

	@Test
	@DisplayName("Access 차단 기록이 Redis 오류로 실패해도 Refresh 기록은 지우고 로그아웃은 끝난다")
	void continuesWhenAccessRevokeFails() {
		String accessToken = jwtProvider.issueAccessToken(MEMBER_ID);
		String refreshToken = jwtProvider.issueRefreshToken(MEMBER_ID, "family-1", NOW.plus(Duration.ofDays(14)));
		doThrow(new QueryTimeoutException("redis down")).when(revokedAccessTokenRepository)
			.revoke(anyString(), any(Duration.class));

		logoutUseCase.logout(refreshToken, "Bearer " + accessToken);

		verify(refreshTokenRepository).deleteIfFamily(MEMBER_ID, "family-1");
	}
}
