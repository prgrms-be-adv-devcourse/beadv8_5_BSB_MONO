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
			insert into payout_payout_item (account, amount, race_id, record_type, seller_id, payout_id,
				race_course_id, bundle_key)
			values ('ADVANCE', 200000, 7, 'ADVANCE_PAID', 1, 16, 3, 'P16:ADVANCE_PAID:C3')""");
		// 영어 값이 든 회수 · 보류 · 상태 이력 (BUK-68에서 한글로 옮긴다)
		jdbcTemplate.update("""
			insert into payout_payout_recovery (id, amount, race_id, reason_type, recovery_key, requested_at, seller_id,
				status)
			values (4, 200000, 7, 'RACE_CANCELED', 'R7:RACE_CANCELED', '2026-11-02 03:00:00', 1, 'REQUESTED')""");
		jdbcTemplate.update("""
			insert into payout_payout_hold (held_at, held_by, reason_type, status, payout_id)
			values ('2026-11-01 04:00:00', 0, 'ACCOUNT_NOT_REGISTERED', 'RELEASED', 16)""");
		jdbcTemplate.update("""
			insert into payout_payout_status_history (changed_at, changed_by, from_status, to_status, payout_id)
			values ('2026-11-01 04:00:00', 0, 'CALCULATED', 'HELD', 16)""");

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
			select concat(p.id, ':', p.payout_stage, ':', p.status, ':', i.account, ':', i.record_type, ':', i.amount)
			from payout_payout p join payout_payout_item i on i.payout_id = p.id""", String.class))
			.isEqualTo("16:일차_정산:지급_완료:선금:선금:200000");
		// 중복 방지 키도 새 코드가 만드는 한글 키와 같아야 같은 줄 · 회수가 두 번 저장되지 않는다
		assertThat(jdbcTemplate.queryForObject("select bundle_key from payout_payout_item", String.class))
			.isEqualTo("P16:선금:C3");
		assertThat(jdbcTemplate.queryForObject("""
			select concat(reason_type, '|', recovery_key, '|', status) from payout_payout_recovery""", String.class))
			.isEqualTo("대회_취소|R7:대회_취소|회수_요청");
		assertThat(jdbcTemplate.queryForObject("""
			select concat(reason_type, ':', status) from payout_payout_hold""", String.class))
			.isEqualTo("정산_계좌_미등록:해제");
		assertThat(jdbcTemplate.queryForObject("""
			select concat(from_status, ':', to_status) from payout_payout_status_history""", String.class))
			.isEqualTo("정산_예정:지급보류");
		assertThat(jdbcTemplate.queryForObject("select count(*) from payout_payout_claim", Integer.class)).isZero();
	}
}
