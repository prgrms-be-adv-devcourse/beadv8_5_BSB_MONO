package com.bukang.member.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.config.auth.RefreshTokenProperties;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenIssueUseCase {
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final RefreshTokenProperties refreshTokenProperties;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	// 로그인마다 새 family를 만든다. 이후 재발급(회전)은 이 family를 이어받고, 다른 기기에서 다시 로그인하면 family가 바뀐다
	// family는 로그인부터 최대 기간(30일)까지만 이어 갈 수 있어, 첫 토큰의 만료도 그 상한을 넘지 않는다
	public AuthTokens issue(long memberId) {
		Instant now = clock.instant();
		Instant familyExpiresAt = now.plus(refreshTokenProperties.maxLifetime());
		Instant tokenExpiresAt = earlier(now.plus(jwtProperties.refreshTokenExpiration()), familyExpiresAt);
		String familyId = UUID.randomUUID().toString();

		String accessToken = jwtProvider.issueAccessToken(memberId);
		String refreshToken = jwtProvider.issueRefreshToken(memberId, familyId, tokenExpiresAt);
		// jti는 JwtProvider가 정하므로, Redis에 적으려고 방금 만든 토큰에서 다시 꺼낸다
		String jti = jwtProvider.parse(refreshToken, TokenType.REFRESH).jti();

		saveFamily(memberId, new NewFamily(familyId, jti, now, tokenExpiresAt, familyExpiresAt));

		return new AuthTokens(accessToken, refreshToken, Duration.between(now, tokenExpiresAt));
	}

	// Redis 장애로 저장하지 못해도 로그인은 막지 않는다.
	// 재발급은 Redis에 저장된 family와 맞아야만 통과하므로, 저장되지 않은 Refresh Token은 재발급에 쓸 수 없다.
	// 그래서 보안은 그대로이고, 사용자는 Access Token이 만료되면(30분) 다시 로그인하면 된다.
	private void saveFamily(long memberId, NewFamily family) {
		try {
			refreshTokenRepository.create(memberId, family);
		} catch (DataAccessException e) {
			// 토큰 값은 로그에 남기지 않는다
			log.warn("Refresh Token을 Redis에 저장하지 못했습니다. 이 로그인은 재발급할 수 없습니다. memberId={}", memberId, e);
		}
	}

	private static Instant earlier(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}
}
