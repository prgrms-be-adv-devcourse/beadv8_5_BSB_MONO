package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.config.auth.RefreshTokenProperties;
import com.bukang.member.domain.exception.InvalidRefreshTokenException;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.Rotation;
import com.bukang.member.out.RotationOutcome;
import com.bukang.member.out.RotationResult;

// 판정(ROTATED·GRACE·…) 자체는 RefreshTokenRepositoryTest가 진짜 Redis로 확인한다. 여기서는 판정에 따라 무엇을 돌려주는지 본다
class TokenReissueUseCaseTest {
	private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
		"jwt-test-secret-key-0000-0000-0000-0000", Duration.ofMinutes(30), Duration.ofDays(14));
	private static final RefreshTokenProperties REFRESH_PROPERTIES =
		new RefreshTokenProperties(Duration.ofSeconds(5), Duration.ofDays(30));
	private static final Instant LOGIN_AT = Instant.parse("2026-10-13T00:00:00Z");
	private static final Instant NOW = LOGIN_AT.plus(Duration.ofDays(1));
	private static final long MEMBER_ID = 7;
	private static final String FAMILY_ID = "family-1";

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final JwtProvider jwtProvider = new JwtProvider(JWT_PROPERTIES, clock);
	private RefreshTokenRepository repository;
	private TokenReissueUseCase useCase;
	private String presentedToken;

	@BeforeEach
	void setUp() {
		repository = mock(RefreshTokenRepository.class);
		useCase = new TokenReissueUseCase(jwtProvider, JWT_PROPERTIES, REFRESH_PROPERTIES, repository, clock);
		presentedToken = jwtProvider.issueRefreshToken(MEMBER_ID, FAMILY_ID, NOW.plus(Duration.ofDays(13)));
		given(repository.findFamilyExpiresAt(MEMBER_ID)).willReturn(Optional.of(LOGIN_AT.plus(Duration.ofDays(30))));
	}

	@Test
	@DisplayName("ROTATED: 새 Access Token과 같은 family의 새 Refresh Token(14일)을 돌려준다")
	void returnsNewTokensWhenRotated() {
		given(repository.rotate(eq(MEMBER_ID), any(Rotation.class)))
			.willReturn(RotationResult.of(RotationOutcome.ROTATED));

		AuthTokens tokens = useCase.reissue(presentedToken);

		JwtClaims presented = jwtProvider.parse(presentedToken, TokenType.REFRESH);
		JwtClaims issued = jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH);
		assertThat(issued.familyId()).isEqualTo(FAMILY_ID);
		assertThat(issued.jti()).isNotEqualTo(presented.jti());
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
		assertThat(tokens.refreshTokenExpiration()).isEqualTo(Duration.ofDays(14));
		assertThat(jwtProvider.parse(tokens.accessToken(), TokenType.ACCESS).memberId()).isEqualTo(MEMBER_ID);

		Rotation rotation = sentRotation();
		assertThat(rotation.familyId()).isEqualTo(FAMILY_ID);
		assertThat(rotation.presentedJti()).isEqualTo(presented.jti());
		assertThat(rotation.newJti()).isEqualTo(issued.jti());
		assertThat(rotation.newTokenExpiresAt()).isEqualTo(issued.expiresAt());
		assertThat(rotation.reuseGrace()).isEqualTo(Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("새 Refresh Token의 만료는 로그인 + 30일을 넘지 않는다")
	void capsNewTokenAtFamilyExpiry() {
		Instant familyExpiresAt = NOW.plus(Duration.ofDays(3));
		given(repository.findFamilyExpiresAt(MEMBER_ID)).willReturn(Optional.of(familyExpiresAt));
		given(repository.rotate(eq(MEMBER_ID), any(Rotation.class)))
			.willReturn(RotationResult.of(RotationOutcome.ROTATED));

		AuthTokens tokens = useCase.reissue(presentedToken);

		assertThat(jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH).expiresAt()).isEqualTo(familyExpiresAt);
		assertThat(tokens.refreshTokenExpiration()).isEqualTo(Duration.ofDays(3));
	}

	@Test
	@DisplayName("GRACE: Redis의 jti·만료 시각으로 지금 토큰과 같은 Refresh Token을 다시 서명해 돌려준다")
	void resignsCurrentTokenWithinGrace() {
		Instant currentExpiresAt = NOW.plus(Duration.ofDays(14));
		given(repository.rotate(eq(MEMBER_ID), any(Rotation.class)))
			.willReturn(new RotationResult(RotationOutcome.GRACE, "J-current", currentExpiresAt));

		AuthTokens tokens = useCase.reissue(presentedToken);

		JwtClaims issued = jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH);
		assertThat(issued.memberId()).isEqualTo(MEMBER_ID);
		assertThat(issued.familyId()).isEqualTo(FAMILY_ID);
		assertThat(issued.jti()).isEqualTo("J-current");
		assertThat(issued.expiresAt()).isEqualTo(currentExpiresAt);
		assertThat(tokens.refreshTokenExpiration()).isEqualTo(Duration.ofDays(14));
	}

	@Test
	@DisplayName("GRACE인데 저장된 만료 시각이 이미 지났으면 토큰을 만들지 않고 401로 거절한다")
	void rejectsGraceWithExpiredCurrentToken() {
		given(repository.rotate(eq(MEMBER_ID), any(Rotation.class)))
			.willReturn(new RotationResult(RotationOutcome.GRACE, "J-current", NOW));

		assertThatThrownBy(() -> useCase.reissue(presentedToken))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.INVALID_MESSAGE);
	}

	@ParameterizedTest
	@DisplayName("THEFT·STALE·MISSING이면 401로 거절한다")
	@EnumSource(value = RotationOutcome.class, names = {"THEFT", "STALE", "MISSING"})
	void rejectsWhenNotRotated(RotationOutcome outcome) {
		given(repository.rotate(eq(MEMBER_ID), any(Rotation.class))).willReturn(RotationResult.of(outcome));

		assertThatThrownBy(() -> useCase.reissue(presentedToken))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.INVALID_MESSAGE);
	}

	@Test
	@DisplayName("Redis에 기록이 없으면 판정 전에 401로 거절한다")
	void rejectsWhenNoFamilyStored() {
		given(repository.findFamilyExpiresAt(MEMBER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reissue(presentedToken))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.INVALID_MESSAGE);
		verify(repository, never()).rotate(anyLong(), any(Rotation.class));
	}

	@Test
	@DisplayName("family 최종 만료가 지났으면 만료 메시지로 거절한다")
	void rejectsWhenFamilyExpired() {
		given(repository.findFamilyExpiresAt(MEMBER_ID)).willReturn(Optional.of(NOW));

		assertThatThrownBy(() -> useCase.reissue(presentedToken))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.EXPIRED_MESSAGE);
	}

	@Test
	@DisplayName("Refresh Token이 없으면 거절한다")
	void rejectMissingToken() {
		assertThatThrownBy(() -> useCase.reissue(null))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.MISSING_MESSAGE);
	}

	@Test
	@DisplayName("만료된 Refresh Token은 Redis를 보지 않고 만료 메시지로 거절한다")
	void rejectExpiredToken() {
		JwtProvider pastProvider = new JwtProvider(JWT_PROPERTIES, Clock.fixed(LOGIN_AT, ZoneOffset.UTC));
		String expiredToken = pastProvider.issueRefreshToken(MEMBER_ID, FAMILY_ID, LOGIN_AT.plusSeconds(60));

		assertThatThrownBy(() -> useCase.reissue(expiredToken))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.EXPIRED_MESSAGE);
		verify(repository, never()).findFamilyExpiresAt(anyLong());
	}

	@Test
	@DisplayName("Access Token을 Refresh Token 자리에 보내면 거절한다")
	void rejectAccessToken() {
		assertThatThrownBy(() -> useCase.reissue(jwtProvider.issueAccessToken(MEMBER_ID)))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage(TokenReissueUseCase.INVALID_MESSAGE);
	}

	private Rotation sentRotation() {
		ArgumentCaptor<Rotation> captor = ArgumentCaptor.forClass(Rotation.class);
		verify(repository).rotate(eq(MEMBER_ID), captor.capture());
		return captor.getValue();
	}
}
