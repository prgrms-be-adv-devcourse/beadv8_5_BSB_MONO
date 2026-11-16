package com.bukang.member.out;

import java.time.Instant;

/**
 * 재발급 판정 결과.
 *
 * @param currentJti GRACE일 때만 있는, 지금 유효한 Refresh Token의 jti. 그 밖에는 null
 * @param currentExpiresAt GRACE일 때만 있는, 지금 유효한 Refresh Token의 만료 시각. 그 밖에는 null
 */
public record RotationResult(RotationOutcome outcome, String currentJti, Instant currentExpiresAt) {

	public static RotationResult of(RotationOutcome outcome) {
		return new RotationResult(outcome, null, null);
	}
}
