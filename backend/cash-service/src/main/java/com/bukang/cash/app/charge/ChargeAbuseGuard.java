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

	// 쿨다운·잠금 중인지만 본다. 시도 횟수는 올리지 않는다 — 멱등 재확인·동시 중복 요청까지 "새 시도"로 세지 않기 위해
	// 지갑 잠금·선점보다 먼저 불러, 이미 막힌 회원은 DB를 건드리기 전에 빠르게 거절한다
	void assertNotCoolingDown(long customerId) {
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
		} catch (DataAccessException e) {
			log.warn("충전 이상 감지(쿨다운) 체크 중 Redis 장애로 건너뜁니다. customerId={}", customerId, e);
		}
	}

	// 지갑 잠금 안에서 새로 선점한(= 진짜 새 시도인) 충전만 횟수를 센다. 멱등 재확인(이미 COMPLETED·IN_PROGRESS)은 부르지 않는다.
	// 5분 고정 구간 안에 이 횟수를 넘겨 시도하면(4번째부터) 10분 쿨다운을 건다
	// (던진 예외가 선점 트랜잭션을 롤백시켜, 쿨다운에 걸린 시도는 충전 행을 남기지 않는다)
	void registerNewAttempt(long customerId) {
		try {
			String attemptsKey = "charge:" + customerId + ":attempts";
			// INCR보다 먼저 SET NX EX로 키·타이머를 깔아 둔다. 이러면 그 뒤 INCR이 어떻게 되든
			// (실패해도) 타이머 없는 카운터가 남지 않는다 — 이미 있는 키는 손대지 않아 고정 구간이 그대로 유지된다
			redisTemplate.opsForValue().setIfAbsent(attemptsKey, "0", ATTEMPT_WINDOW);
			Long count = redisTemplate.opsForValue().increment(attemptsKey);
			if (count != null && count > CashChargePolicy.MAX_CHARGE_ATTEMPTS_PER_WINDOW) {
				redisTemplate.opsForValue().set("charge:" + customerId + ":cooldown", "1", COOLDOWN_DURATION);
				log.warn("충전 어뷰징 감지: 짧은 시간에 반복 시도로 쿨다운이 걸렸습니다. customerId={}", customerId);
				throw new ChargeCooldownException(cooldownMessage(COOLDOWN_DURATION.toSeconds()));
			}
		} catch (DataAccessException e) {
			log.warn("충전 시도 횟수 기록 중 Redis 장애로 건너뜁니다. customerId={}", customerId, e);
		}
	}

	// 1시간 고정 구간 안에 PG가 명확히 거절한 횟수가 이 수를 넘으면(6번째부터) 1시간 충전 잠금을 건다
	void registerDefiniteFailure(long customerId) {
		try {
			String failuresKey = "charge:" + customerId + ":failures";
			// 이유는 registerNewAttempt와 같다 — INCR 전에 타이머부터 보장해 둔다
			redisTemplate.opsForValue().setIfAbsent(failuresKey, "0", FAILURE_WINDOW);
			Long count = redisTemplate.opsForValue().increment(failuresKey);
			if (count != null && count > CashChargePolicy.MAX_CHARGE_FAILURES_PER_WINDOW) {
				redisTemplate.opsForValue().set("charge:" + customerId + ":lock", "1", LOCK_DURATION);
				log.warn("충전 어뷰징 감지: 결제 실패 반복으로 충전이 잠겼습니다. customerId={}", customerId);
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
