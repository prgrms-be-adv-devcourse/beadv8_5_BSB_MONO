package com.bukang.order;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Flyway를 넣기 전 서버·로컬 DB처럼 Hibernate(ddl-auto)가 이미 주문 표를 만든 DB에서 처음 Flyway가 도는 경우 (BUK-29)
// V1은 create table if not exists라 표와 데이터를 그대로 두고, 기록 표에 "적용함"만 남겨야 한다
// 인덱스·제약을 create table 밖(create index, alter table)에 두면 여기서 "이미 있음"으로 실패한다
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:order-flyway-existing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create"
})
@ActiveProfiles("test")
class FlywayExistingTablesTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("주문 표가 이미 있는 DB에서도 V1이 실패 없이 돌고, 표와 데이터는 바뀌지 않는다")
	void keepsExistingTables() {
		jdbcTemplate.update("insert into order_cart (member_id) values (1)");

		// dev·운영과 같은 Flyway 설정 (BUK-29)
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_order")
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.outOfOrder(true)
			.load()
			.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isPositive();
		assertThat(jdbcTemplate.queryForObject("select count(*) from order_cart", Integer.class)).isEqualTo(1);
	}
}
