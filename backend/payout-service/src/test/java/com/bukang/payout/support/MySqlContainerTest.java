package com.bukang.payout.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 동시성 테스트(@DataJpaTest)를 H2 대신 진짜 MySQL(Testcontainers, Docker 필요)에서 돌린다 (BUK-242, order-service BUK-188과 같은 이유)
 * H2는 격리 수준 기본값(READ COMMITTED)과 잠금 방식이 MySQL(REPEATABLE READ, 갭·next-key 잠금)과 달라 옛 상태 읽기·교착을 못 잡는다
 * 표는 Hibernate가 아니라 운영과 같은 Flyway SQL로 만든다 (유니크 제약 이름·인덱스가 운영과 같아야 잠금 범위가 같다)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
	"spring.flyway.enabled=true",
	// dev·운영과 같은 Flyway 설정 (FlywayBaselineValidateTest와 같다)
	"spring.flyway.table=flyway_schema_history_payout",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@Import(MySqlContainerTest.Container.class)
public @interface MySqlContainerTest {

	@TestConfiguration(proxyBeanMethods = false)
	class Container {
		// 테스트 클래스마다 @Import가 달라 컨텍스트가 여러 개 생긴다. 컨테이너는 하나만 띄워 함께 쓴다
		private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4"); // compose.yml과 같은 버전

		@Bean
		@ServiceConnection
		MySQLContainer mysqlContainer() {
			return MYSQL;
		}
	}
}
