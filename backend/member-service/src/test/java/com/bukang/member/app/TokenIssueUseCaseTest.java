package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.config.auth.RefreshTokenProperties;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;

class TokenIssueUseCaseTest {
	private static final long MEMBER_ID = 7L;
	private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
	private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
		"jwt-test-secret-key-0000-1111-2222-3333", Duration.ofMinutes(30), Duration.ofDays(14));

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final JwtProvider jwtProvider = new JwtProvider(JWT_PROPERTIES, clock);
	private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);

	@Test
	@DisplayName("로그인하면 새 family를 만들고, 토큰은 14일, family는 30일 뒤 만료로 저장한다")
	void createsNewFamily() {
		AuthTokens tokens = useCase(Duration.ofDays(30)).issue(MEMBER_ID);

		JwtClaims claims = jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH);
		NewFamily family = savedFamily();
		assertThat(family.familyId()).isEqualTo(claims.familyId());
		assertThat(family.jti()).isEqualTo(claims.jti());
		assertThat(family.issuedAt()).isEqualTo(NOW);
		assertThat(family.tokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
		assertThat(family.familyExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
		assertThat(tokens.refreshTokenExpiration()).isEqualTo(Duration.ofDays(14));
	}

	@Test
	@DisplayName("최대 기간이 14일보다 짧으면 첫 토큰의 만료도 그 상한에서 자른다")
	void capsFirstTokenAtMaxLifetime() {
		AuthTokens tokens = useCase(Duration.ofDays(7)).issue(MEMBER_ID);

		assertThat(jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH).expiresAt())
			.isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(tokens.refreshTokenExpiration()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	@DisplayName("로그인할 때마다 family가 새로 만들어진다")
	void eachLoginStartsNewFamily() {
		TokenIssueUseCase useCase = useCase(Duration.ofDays(30));

		String first = jwtProvider.parse(useCase.issue(MEMBER_ID).refreshToken(), TokenType.REFRESH).familyId();
		String second = jwtProvider.parse(useCase.issue(MEMBER_ID).refreshToken(), TokenType.REFRESH).familyId();

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	@DisplayName("Redis에 저장하지 못해도 토큰은 발급한다")
	void issuesTokensWhenRedisIsDown() {
		willThrow(new RedisConnectionFailureException("Redis 연결 실패"))
			.given(repository).create(anyLong(), any(NewFamily.class));

		AuthTokens tokens = useCase(Duration.ofDays(30)).issue(MEMBER_ID);

		assertThat(tokens.accessToken()).isNotBlank();
		assertThat(tokens.refreshToken()).isNotBlank();
	}

	private TokenIssueUseCase useCase(Duration maxLifetime) {
		RefreshTokenProperties properties = new RefreshTokenProperties(Duration.ofSeconds(5), maxLifetime);
		return new TokenIssueUseCase(jwtProvider, JWT_PROPERTIES, properties, repository, clock);
	}

	private NewFamily savedFamily() {
		ArgumentCaptor<NewFamily> captor = ArgumentCaptor.forClass(NewFamily.class);
		verify(repository).create(eq(MEMBER_ID), captor.capture());
		return captor.getValue();
	}
}
