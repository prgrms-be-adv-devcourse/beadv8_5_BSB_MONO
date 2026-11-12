package com.bukang.order.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

/**
 * H2 테스트를 상속한 클래스에 붙여 같은 시나리오를 진짜 MySQL(Testcontainers, Docker 필요)에서 다시 돌린다 (BUK-188)
 * H2는 격리 수준 기본값(READ COMMITTED)과 잠금 방식이 MySQL(REPEATABLE READ, 갭·next-key 잠금)과 달라 교착·옛 상태 읽기를 못 잡는다
 * 표는 Hibernate가 아니라 운영과 같은 Flyway SQL로 만든다 (유니크 제약 이름·인덱스가 운영과 같아야 잠금 범위가 같다)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
	"spring.flyway.enabled=true",
	"spring.flyway.table=flyway_schema_history_order",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@Import(MySqlContainerTest.Container.class)
public @interface MySqlContainerTest {

	@TestConfiguration(proxyBeanMethods = false)
	class Container {
		@Bean
		@ServiceConnection
		MySQLContainer mysqlContainer() {
			return new MySQLContainer("mysql:8.4"); // compose.yml과 같은 버전
		}
	}
}
