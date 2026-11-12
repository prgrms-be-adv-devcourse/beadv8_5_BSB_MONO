package com.bukang.member.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.bukang.jwt.InvalidTokenException;
import com.bukang.jwt.JwtClaims;
import com.bukang.jwt.JwtProperties;
import com.bukang.jwt.JwtProvider;
import com.bukang.jwt.TokenType;
import com.bukang.member.config.auth.RefreshTokenProperties;
import com.bukang.member.domain.exception.InvalidRefreshTokenException;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.Rotation;
import com.bukang.member.out.RotationResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenReissueUseCase {
	static final String MISSING_MESSAGE = "Refresh Token이 없습니다. 다시 로그인해주세요.";
	static final String EXPIRED_MESSAGE = "Refresh Token이 만료되었습니다. 다시 로그인해주세요.";
	static final String INVALID_MESSAGE = "유효하지 않은 Refresh Token입니다. 다시 로그인해주세요.";

	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final RefreshTokenProperties refreshTokenProperties;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	// Refresh Token을 회전한다: 새 Access Token과 함께 Refresh Token도 새것으로 바꿔 준다.
	// 1. 서명·만료를 검사해 회원 ID, familyId, jti를 꺼낸다 (위조·만료 토큰은 Redis까지 가지 않는다)
	// 2. 다음 Refresh Token을 미리 만든다. 만료는 지금부터 14일, 단 family 최종 만료(로그인 + 30일)를 넘지 않는다
	// 3. Redis(Lua)가 판정하고 정상이면 새 토큰으로 바꾼다. 판정에 따라 새 토큰, 지금 토큰(유예), 401 중 하나다
	public AuthTokens reissue(String refreshToken) {
		JwtClaims claims = verify(refreshToken);
		long memberId = claims.memberId();
		Instant now = clock.instant();

		Instant familyExpiresAt = refreshTokenRepository.findFamilyExpiresAt(memberId)
			.orElseThrow(() -> new InvalidRefreshTokenException(INVALID_MESSAGE));
		Instant newExpiresAt = earlier(now.plus(jwtProperties.refreshTokenExpiration()), familyExpiresAt);
		if (!newExpiresAt.isAfter(now)) {
			// family 최종 만료가 지났다. 보통은 키 TTL로 먼저 사라지지만, 시계 차이로 남아 있을 수 있다
			throw new InvalidRefreshTokenException(EXPIRED_MESSAGE);
		}
		String newToken = jwtProvider.issueRefreshToken(memberId, claims.familyId(), newExpiresAt);
		String newJti = jwtProvider.parse(newToken, TokenType.REFRESH).jti();

		RotationResult result = refreshTokenRepository.rotate(memberId, new Rotation(
			claims.familyId(), claims.jti(), newJti, now, refreshTokenProperties.reuseGrace(), newExpiresAt));

		String issuedRefreshToken = switch (result.outcome()) {
			case ROTATED -> newToken;
			// 유예: 방금 회전한 요청이 받은 토큰과 같은 jti·만료로 다시 서명해 돌려준다. 미리 만든 newToken은 버린다
			case GRACE -> regraceToken(memberId, claims.familyId(), result);
			case THEFT -> {
				// 토큰 값은 로그에 남기지 않는다
				log.warn("이미 쓴 Refresh Token이 다시 들어와 family를 끊었습니다. 탈취가 의심됩니다. memberId={}", memberId);
				throw new InvalidRefreshTokenException(INVALID_MESSAGE);
			}
			// STALE: 다른 기기에서 새로 로그인했다. MISSING: 로그아웃, 탈취 처리, TTL 만료
			case STALE, MISSING -> throw new InvalidRefreshTokenException(INVALID_MESSAGE);
		};

		Instant refreshExpiresAt = jwtProvider.parse(issuedRefreshToken, TokenType.REFRESH).expiresAt();
		return new AuthTokens(jwtProvider.issueAccessToken(memberId), issuedRefreshToken,
			Duration.between(now, refreshExpiresAt));
	}

	private String regraceToken(long memberId, String familyId, RotationResult result) {
		try {
			return jwtProvider.reissueRefreshToken(memberId, familyId, result.currentJti(), result.currentExpiresAt());
		} catch (IllegalArgumentException e) {
			// 저장된 만료 시각이 이미 지났다. 키 TTL이 먼저 지우므로 보통 오지 않지만, 시계 차이로 남아 있을 수 있다
			throw new InvalidRefreshTokenException(INVALID_MESSAGE);
		}
	}

	private JwtClaims verify(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException(MISSING_MESSAGE);
		}

		JwtClaims claims;
		try {
			claims = jwtProvider.parse(refreshToken, TokenType.REFRESH);
		} catch (InvalidTokenException e) {
			if (e.getReason() == InvalidTokenException.Reason.EXPIRED) {
				throw new InvalidRefreshTokenException(EXPIRED_MESSAGE);
			}
			throw new InvalidRefreshTokenException(INVALID_MESSAGE);
		}

		// 회전 도입 전에 발급된 토큰에는 familyId가 없다. 다시 로그인하게 한다
		if (claims.familyId() == null || claims.jti() == null) {
			throw new InvalidRefreshTokenException(INVALID_MESSAGE);
		}
		return claims;
	}

	private static Instant earlier(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
	}
}
