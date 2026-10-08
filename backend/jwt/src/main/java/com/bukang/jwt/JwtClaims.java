package com.bukang.jwt;

import java.time.Instant;

/**
 * 검증을 통과한 토큰에서 꺼낸 값.
 */
public record JwtClaims(long memberId, TokenType type, Instant expiresAt) {
}
