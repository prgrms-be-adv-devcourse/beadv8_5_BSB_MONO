package com.bukang.cash.app.charge;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.bukang.cash.domain.charge.CashChargePolicy;
import com.bukang.cash.domain.exception.charge.ChargeCooldownException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이상 충전 패턴 감지(쿨다운·잠금). Redis 고정 구간 카운터로 센다 (redis-key-convention.md).
 * 어뷰징 감지는 부가 안전장치라, Redis 장애로 충전이라는 핵심 기능까지 멈추면 안 된다(fail-open) — 장애 시 체크를 건너뛴다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class ChargeAbuseGuard {
	private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(5);
	private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(10);
	private static final Duration FAILURE_WINDOW = Duration.ofHours(1);
	private static final Duration LOCK_DURATION = Duration.ofHours(1);

	private final StringRedisTemplate redisTemplate;

	// 5분 고정 구간 안에 이 횟수를 넘겨 시도하면(4번째부터) 10분 쿨다운을 건다. 성공·실패·입력오류 무관하게 호출 자체를 센다
	void assertAttemptNotRateLimited(long customerId) {
		try {
			String cooldownKey = "charge:" + customerId + ":cooldown";
			Long cooldownTtl = redisTemplate.getExpire(cooldownKey);
			if (cooldownTtl != null && cooldownTtl > 0) {
				throw new ChargeCooldownException(cooldownMessage(cooldownTtl));
			}

			String lockKey = "charge:" + customerId + ":lock";
			Long lockTtl = redisTemplate.getExpire(lockKey);
			if (lockTtl != null && lockTtl > 0) {
				throw new ChargeCooldownException(lockMessage(lockTtl));
			}

			String attemptsKey = "charge:" + customerId + ":attempts";
			Long count = redisTemplate.opsForValue().increment(attemptsKey);
			if (count != null && count == 1) {
				redisTemplate.expire(attemptsKey, ATTEMPT_WINDOW);
			}
			if (count != null && count > CashChargePolicy.MAX_CHARGE_ATTEMPTS_PER_WINDOW) {
				redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);
				throw new ChargeCooldownException(cooldownMessage(COOLDOWN_DURATION.toSeconds()));
			}
		} catch (DataAccessException e) {
			log.warn("충전 이상 감지(쿨다운) 체크 중 Redis 장애로 건너뜁니다. customerId={}", customerId, e);
		}
	}

	// 1시간 고정 구간 안에 PG가 명확히 거절한 횟수가 이 수를 넘으면(6번째부터) 1시간 충전 잠금을 건다
	void registerDefiniteFailure(long customerId) {
		try {
			String failuresKey = "charge:" + customerId + ":failures";
			Long count = redisTemplate.opsForValue().increment(failuresKey);
			if (count != null && count == 1) {
				redisTemplate.expire(failuresKey, FAILURE_WINDOW);
			}
			if (count != null && count > CashChargePolicy.MAX_CHARGE_FAILURES_PER_WINDOW) {
				redisTemplate.opsForValue().set("charge:" + customerId + ":lock", "1", LOCK_DURATION);
			}
		} catch (DataAccessException e) {
			log.warn("충전 실패 횟수 기록 중 Redis 장애로 건너뜁니다. customerId={}", customerId, e);
		}
	}

	// 충전이 한 번이라도 성공하면 정상적으로 결제되고 있다는 뜻이라, 그 전 실패 이력 때문에 계속 위험군으로 남을 이유가 없다
	void resetFailureCount(long customerId) {
		try {
			redisTemplate.delete("charge:" + customerId + ":failures");
		} catch (DataAccessException e) {
			log.warn("충전 실패 횟수 리셋 중 Redis 장애로 건너뜁니다. customerId={}", customerId, e);
		}
	}

	private String cooldownMessage(long remainingSeconds) {
		long minutes = (long)Math.ceil(remainingSeconds / 60.0);
		return "충전 시도가 너무 잦습니다. " + minutes + "분 후 다시 시도해주세요.";
	}

	private String lockMessage(long remainingSeconds) {
		long minutes = (long)Math.ceil(remainingSeconds / 60.0);
		return "최근 결제 실패가 많아 충전이 잠시 제한됐습니다. " + minutes + "분 후 다시 시도해주세요.";
	}
}
