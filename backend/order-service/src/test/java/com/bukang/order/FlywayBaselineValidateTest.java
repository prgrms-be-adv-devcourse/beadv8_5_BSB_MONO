package com.bukang.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 빈 DB에 Flyway로 모든 SQL(V1 기준선 + 이후 변경)을 돌린 뒤 Hibernate validate로 엔티티와 표가 맞는지 확인한다 (BUK-29)
// 엔티티를 고치고 SQL을 빠뜨리면 여기서 실패한다
// H2(MySQL 호환 모드)라 실제 MySQL과 완전히 같지는 않다. 다른 테스트와 섞이지 않게 DB 이름을 따로 쓴다
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:order-flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=true",
	// dev·운영과 같은 Flyway 설정 (BUK-29)
	"spring.flyway.table=flyway_schema_history_order",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class FlywayBaselineValidateTest {

	@Autowired
	private Flyway flyway;

	@Test
	@DisplayName("빈 DB에 모든 SQL을 적용한 표가 엔티티와 맞는다 (Hibernate validate 통과)")
	void migrationsMatchEntities() {
		// validate에 실패하면 컨텍스트가 뜨지 않아 이 테스트까지 오지 못한다. 여기서는 남은 SQL이 없는지만 본다
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current()).isNotNull();
	}
}
