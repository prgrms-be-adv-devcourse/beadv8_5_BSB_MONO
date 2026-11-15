package com.bukang.member.app;

import java.time.Clock;
import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.bukang.jwt.InvalidTokenException;
import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RevokedAccessTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RevokedAccessTokenRepository revokedAccessTokenRepository;
	private final Clock clock;

	// Redis의 Refresh Token 기록(family)을 지워 더 이상 재발급되지 않게 한다.
	// 쿠키가 없거나 만료·위조된 토큰이면 지울 것이 없으므로 그냥 넘어간다 (로그아웃은 항상 성공).
	// 지금 family의 토큰일 때만 지운다. 다른 기기에서 다시 로그인해 family가 바뀌었으면, 지금 로그인한 기기가 로그아웃되지 않도록 남긴다.
	// 같은 family라면 회전으로 이미 바뀐 이전 토큰이어도 지운다 (같은 로그인을 끝내는 것이므로).
	// 요청의 Access Token은 jti를 남은 시간만큼 막힌 토큰으로 남겨 게이트웨이가 바로 막게 한다. 없거나 무효하면 남기지 않는다.
	public void logout(String refreshToken, String authorization) {
		revokeAccessToken(authorization);

		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}

		JwtClaims claims;
		try {
			claims = jwtProvider.parse(refreshToken, TokenType.REFRESH);
		} catch (InvalidTokenException e) {
			return;
		}

		// 회전 도입 전에 발급된 토큰에는 familyId가 없다. 지울 family를 알 수 없으므로 넘어간다
		if (claims.familyId() == null) {
			return;
		}
		refreshTokenRepository.deleteIfFamily(claims.memberId(), claims.familyId());
	}

	private void revokeAccessToken(String authorization) {
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return;
		}
		try {
			String token = authorization.substring(BEARER_PREFIX.length()).trim();
			JwtClaims claims = jwtProvider.parse(token, TokenType.ACCESS);
			Duration remaining = Duration.between(clock.instant(), claims.expiresAt());
			if (remaining.compareTo(Duration.ZERO) > 0) {
				revokedAccessTokenRepository.revoke(claims.jti(), remaining);
			}
		} catch (InvalidTokenException e) {
			// 로그아웃은 항상 성공한다
		} catch (DataAccessException e) {
			// 차단 기록이 실패해도 Refresh 삭제와 쿠키 만료는 계속한다. Access 토큰은 만료 시각에 어차피 끝난다
			log.warn("Access 토큰 차단 기록에 실패했습니다: {}", e.toString());
		}
	}
}
