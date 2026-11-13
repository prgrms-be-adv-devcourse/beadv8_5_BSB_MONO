package com.bukang.member.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.RefreshTokenRepository.Rotation;
import com.redis.testcontainers.RedisContainer;

// Lua 스크립트는 가짜(mock) Redis로는 검증할 수 없어 진짜 Redis 컨테이너로 확인한다 (Docker 필요)
@Testcontainers
class RefreshTokenRepositoryTest {
	private static final long MEMBER_ID = 7L;
	private static final String KEY = "members:7:refreshToken";
	// EXPIREAT은 Redis의 실제 시계로 판단하므로, 고정 날짜를 쓰면 그날이 지난 뒤 키가 바로 사라진다. 지금 시각을 기준으로 한다
	private static final Instant LOGIN_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	private static final Duration GRACE = Duration.ofSeconds(5);

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;
	private static RefreshTokenRepository repository;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(REDIS.getRedisHost(), REDIS.getRedisPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		repository = new RefreshTokenRepository(redisTemplate);
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void login() {
		redisTemplate.delete(KEY);
		repository.create(MEMBER_ID, new NewFamily("F1", "J1",
			LOGIN_AT, LOGIN_AT.plus(Duration.ofDays(14)), LOGIN_AT.plus(Duration.ofDays(30))));
	}

	@Test
	@DisplayName("로그인하면 Hash 6칸을 쓰고 토큰 원문 칸은 없으며, 키 TTL을 토큰 만료에 맞춘다")
	void createWritesHashAndExpiry() {
		assertThat(redisTemplate.opsForHash().entries(KEY)).containsOnlyKeys(
			"familyId", "currentJti", "currentExpiresAt", "previousJti", "rotatedAt", "familyExpiresAt");
		assertThat(redisTemplate.opsForHash().get(KEY, "currentJti")).isEqualTo("J1");
		assertThat(redisTemplate.opsForHash().get(KEY, "currentExpiresAt"))
			.isEqualTo(String.valueOf(LOGIN_AT.plus(Duration.ofDays(14)).getEpochSecond()));
		assertThat(redisTemplate.getExpire(KEY)).isPositive();
		assertThat(repository.findFamilyExpiresAt(MEMBER_ID)).contains(LOGIN_AT.plus(Duration.ofDays(30)));
	}

	@Test
	@DisplayName("다시 로그인하면 이전 family를 통째로 덮어쓴다")
	void createOverwritesPreviousFamily() {
		repository.create(MEMBER_ID, new NewFamily("F2", "K1",
			LOGIN_AT, LOGIN_AT.plus(Duration.ofDays(14)), LOGIN_AT.plus(Duration.ofDays(30))));

		assertThat(redisTemplate.opsForHash().get(KEY, "familyId")).isEqualTo("F2");
		assertThat(redisTemplate.opsForHash().get(KEY, "previousJti")).isEqualTo("");
	}

	@Test
	@DisplayName("ROTATED: 지금 토큰이 오면 새 토큰으로 바꾸고 이전 jti를 previousJti로 옮긴다")
	void rotatesCurrentToken() {
		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", LOGIN_AT.plusSeconds(60)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.ROTATED);
		assertThat(redisTemplate.opsForHash().get(KEY, "currentJti")).isEqualTo("J2");
		assertThat(redisTemplate.opsForHash().get(KEY, "currentExpiresAt"))
			.isEqualTo(String.valueOf(LOGIN_AT.plusSeconds(60).plus(Duration.ofDays(14)).getEpochSecond()));
		assertThat(redisTemplate.opsForHash().get(KEY, "previousJti")).isEqualTo("J1");
	}

	@Test
	@DisplayName("회전해도 Hash에 토큰 원문 칸이 없다")
	void rotationLeavesNoRawTokenField() {
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", LOGIN_AT.plusSeconds(60)));

		assertThat(redisTemplate.opsForHash().keys(KEY)).doesNotContain("currentToken");
	}

	@Test
	@DisplayName("배포 전에 저장된 옛 currentToken 칸은 회전할 때 지워진다")
	void rotationDeletesLegacyCurrentTokenField() {
		redisTemplate.opsForHash().put(KEY, "currentToken", "raw-legacy-jwt");

		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", LOGIN_AT.plusSeconds(60)));

		assertThat(redisTemplate.opsForHash().hasKey(KEY, "currentToken")).isFalse();
	}

	@Test
	@DisplayName("같은 토큰으로 두 번 회전하면 두 번째는 ROTATED가 아니다 (동시 요청이 둘 다 통과하지 못한다)")
	void secondRotationWithSameTokenIsNotRotated() {
		Instant now = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", now));

		RotationResult second = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J3", now));

		assertThat(second.outcome()).isEqualTo(RotationOutcome.GRACE);
		assertThat(redisTemplate.opsForHash().get(KEY, "currentJti")).isEqualTo("J2");
	}

	@Test
	@DisplayName("GRACE: 방금 바뀐 토큰이 유예 안에 오면 바꾸지 않고 지금 토큰의 jti와 만료 시각을 돌려준다")
	void returnsCurrentTokenWithinGrace() {
		Instant rotatedAt = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", rotatedAt));

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J3", rotatedAt.plusSeconds(5)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.GRACE);
		assertThat(result.currentJti()).isEqualTo("J2");
		assertThat(result.currentExpiresAt()).isEqualTo(rotatedAt.plus(Duration.ofDays(14)));
	}

	@Test
	@DisplayName("GRACE에서 돌려준 토큰(J2)으로 다음 재발급이 오면 정상 회전한다")
	void tokenReturnedByGraceRotatesNormally() {
		Instant rotatedAt = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", rotatedAt));
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J3", rotatedAt.plusSeconds(1)));

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J2", "J4", rotatedAt.plusSeconds(2)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.ROTATED);
		assertThat(redisTemplate.opsForHash().get(KEY, "currentJti")).isEqualTo("J4");
	}

	@Test
	@DisplayName("배포 직후 currentExpiresAt이 없는 옛 기록에서 유예가 오면 MISSING처럼 거절하고 원문을 돌려주지 않는다")
	void graceOnLegacyRecordIsMissing() {
		Instant rotatedAt = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", rotatedAt));
		redisTemplate.opsForHash().delete(KEY, "currentExpiresAt");
		redisTemplate.opsForHash().put(KEY, "currentToken", "raw-legacy-jwt");

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J3", rotatedAt.plusSeconds(1)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.MISSING);
		assertThat(result.currentJti()).isNull();
	}

	@Test
	@DisplayName("THEFT: 유예가 지난 뒤 이미 쓴 토큰이 오면 family를 지운다")
	void deletesFamilyOnReuseAfterGrace() {
		Instant rotatedAt = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", rotatedAt));

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J3", rotatedAt.plusSeconds(6)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.THEFT);
		assertThat(redisTemplate.hasKey(KEY)).isFalse();
	}

	@Test
	@DisplayName("THEFT: 바로 전보다 더 오래된 토큰은 유예 안이어도 탈취로 본다")
	void deletesFamilyOnOlderTokenEvenWithinGrace() {
		Instant now = LOGIN_AT.plusSeconds(60);
		repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", now));
		repository.rotate(MEMBER_ID, rotation("F1", "J2", "J3", now.plusSeconds(1)));

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J4", now.plusSeconds(2)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.THEFT);
	}

	@Test
	@DisplayName("STALE: family가 다르면 아무것도 바꾸지 않는다 (다른 기기에서 새로 로그인함)")
	void leavesNewFamilyUntouchedForStaleFamily() {
		RotationResult result = repository.rotate(MEMBER_ID, rotation("OLD", "X1", "X2", LOGIN_AT.plusSeconds(60)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.STALE);
		assertThat(redisTemplate.opsForHash().get(KEY, "currentJti")).isEqualTo("J1");
	}

	@Test
	@DisplayName("MISSING: 기록이 없으면 MISSING이다")
	void missingWhenNoRecord() {
		redisTemplate.delete(KEY);

		RotationResult result = repository.rotate(MEMBER_ID, rotation("F1", "J1", "J2", LOGIN_AT.plusSeconds(60)));

		assertThat(result.outcome()).isEqualTo(RotationOutcome.MISSING);
	}

	@Test
	@DisplayName("로그아웃: 같은 family면 지우고, 다른 family면 남긴다")
	void deletesOnlySameFamily() {
		assertThat(repository.deleteIfFamily(MEMBER_ID, "OLD")).isFalse();
		assertThat(redisTemplate.hasKey(KEY)).isTrue();

		assertThat(repository.deleteIfFamily(MEMBER_ID, "F1")).isTrue();
		assertThat(redisTemplate.hasKey(KEY)).isFalse();
	}

	private static Rotation rotation(String familyId, String presentedJti, String newJti, Instant now) {
		return new Rotation(familyId, presentedJti, newJti,
			now, GRACE, now.plus(Duration.ofDays(14)));
	}
}
