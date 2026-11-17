package com.bukang.jwt;

import java.time.Instant;

/**
 * 검증을 통과한 토큰에서 꺼낸 값.
 *
 * @param jti 토큰마다 다른 무작위 ID. Refresh Token을 회전할 때 지금 토큰인지 대조하는 데 쓴다
 * @param familyId 같은 로그인에서 회전으로 이어진 Refresh Token 묶음 ID. Access Token에는 없다(null)
 */
public record JwtClaims(long memberId, TokenType type, Instant expiresAt, String jti, String familyId) {
}
