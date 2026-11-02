package com.bukang.payout;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

// 운영 DB처럼 Flyway 기록 없이 정산 표와 데이터가 이미 있는 DB에서 처음 Flyway가 도는 경우 (PR #265)
// V1은 2026-11-02 운영 표 그대로라, V1을 Flyway 밖에서 먼저 돌려 운영 표를 만든다
// 이후 SQL이 데이터를 지키며 ID를 bigint로, 원장 칸을 ENUM으로 바꾸고 청구 표를 만들어야 한다
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:payout-flyway-existing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
class FlywayExistingTablesTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("운영과 같은 표 · 데이터가 있는 DB에서 Flyway가 실패 없이 돌고, 데이터는 그대로 남는다")
	void migratesExistingTablesKeepingData() {
		new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__baseline.sql")).execute(dataSource);
		jdbcTemplate.update("""
			insert into payout_payout (id, advance_deduction_amount, fee_amount, payout_amount, payout_month,
				payout_stage, race_id, race_name, refund_amount, sales_amount, scheduled_pay_date, seller_id,
				settled_at, status, version)
			values (16, 0, 0, 200000, '2026-11', 'FIRST', 7, '대회 A', 0, 500000, '2026-11-20', 1,
				'2026-11-01 03:00:00', 'PAID', 0)""");
		jdbcTemplate.update("""
			insert into payout_payout_item (account, amount, race_id, record_type, seller_id, payout_id)
			values ('ADVANCE', 200000, 7, 'ADVANCE_PAID', 1, 16)""");

		// dev · 운영과 같은 Flyway 설정 (PR #265)
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_payout")
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.outOfOrder(true)
			.load()
			.migrate();

		assertThat(result.success).isTrue();
		assertThat(jdbcTemplate.queryForObject("""
			select concat(p.id, ':', i.account, ':', i.record_type, ':', i.amount)
			from payout_payout p join payout_payout_item i on i.payout_id = p.id""", String.class))
			.isEqualTo("16:ADVANCE:ADVANCE_PAID:200000");
		assertThat(jdbcTemplate.queryForObject("select count(*) from payout_payout_claim", Integer.class)).isZero();
	}
}
