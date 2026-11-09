package com.bukang.cash;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

import com.redis.testcontainers.RedisContainer;

/**
 * 테스트용 Redis 컨테이너. 이상 충전 패턴 감지(쿨다운·잠금)처럼 TTL·원자적 증가를 확인해야 하는
 * 테스트 클래스에 @Import로 붙인다 (Docker 필요, compose와 같은 버전).
 * 접속 주소(spring.data.redis.host, port)는 @ServiceConnection이 넣어 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {
	@Bean
	@ServiceConnection
	RedisContainer redisContainer() {
		return new RedisContainer("redis:8.2");
	}
}
