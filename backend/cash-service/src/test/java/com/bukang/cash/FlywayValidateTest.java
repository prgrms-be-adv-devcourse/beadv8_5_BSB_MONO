package com.bukang.cash;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 빈 MySQL에 Flyway로 모든 SQL을 돌린 뒤 Hibernate validate로 엔티티와 표가 맞는지 확인한다
// 엔티티를 고치고 SQL을 빠뜨리면 컨텍스트가 뜨지 않아 여기서 실패한다
// cash 표는 MySQL ENUM 칸이 있어 H2가 아니라 진짜 MySQL(Testcontainers)로 돌린다
@SpringBootTest(properties = {
	"spring.flyway.enabled=true",
	// dev·운영과 같은 Flyway 설정
	"spring.flyway.table=flyway_schema_history_cash",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FlywayValidateTest {

	@Autowired
	private Flyway flyway;

	@Test
	@DisplayName("빈 MySQL에 모든 SQL을 적용한 표가 엔티티와 맞는다 (Hibernate validate 통과)")
	void migrationsMatchEntities() {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current()).isNotNull();
	}
}
