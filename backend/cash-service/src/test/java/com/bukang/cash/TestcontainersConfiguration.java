package com.bukang.cash;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL 컨테이너. MySQL에서 확인해야 하는 테스트 클래스에 @Import로 붙인다 (Docker 필요, compose와 같은 버전).
 * 나머지 테스트는 H2(test 프로파일)로 돈다. H2는 격리 수준 기본값이 MySQL과 달라(READ COMMITTED, MySQL은 REPEATABLE READ)
 * 동시 처리 결과가 다르게 나올 수 있다.
 * 접속 주소(spring.datasource.url 등)는 @ServiceConnection이 넣어 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer("mysql:8.4");
	}
}
