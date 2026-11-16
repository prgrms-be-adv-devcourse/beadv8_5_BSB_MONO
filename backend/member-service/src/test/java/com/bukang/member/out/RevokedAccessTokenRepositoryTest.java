package com.bukang.member.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RevokedAccessTokenRepositoryTest {

	@Test
	@DisplayName("키 형식은 게이트웨이가 확인하는 access-tokens:{jti}:revoked와 같다")
	void keyMatchesGateway() {
		// 게이트웨이(JwtAuthenticationFilter)는 같은 형식을 자기 상수로 둔다. 한쪽만 바꾸면 이 값과 게이트웨이 테스트가 함께 깨진다
		assertThat(RevokedAccessTokenRepository.key("abc")).isEqualTo("access-tokens:abc:revoked");
	}

	@Test
	@DisplayName("남은 시간을 TTL로 지정해 저장한다")
	@SuppressWarnings("unchecked")
	void savesWithTtl() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(values);

		new RevokedAccessTokenRepository(template).revoke("abc", Duration.ofMinutes(12));

		verify(values).set("access-tokens:abc:revoked", "1", Duration.ofMinutes(12));
	}
}
