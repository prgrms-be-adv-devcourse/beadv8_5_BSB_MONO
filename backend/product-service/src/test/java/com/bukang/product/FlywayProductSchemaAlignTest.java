package com.bukang.product;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 운영 상품 표가 코드와 어긋난 상태(CRU-88)에서 ProductSchemaAlignMigration이 표를 코드와 맞추는지 본다
// 운영 상태: 자리 확보 표에 participation_id 유니크가 없고, 옛 칸 application_id(옛 유니크)·crew_application_id가 남아 있으며,
// 칸 이름을 바꿀 때 생긴 옛 행은 participation_id가 0이다. race_course에는 지운 칸 tshirt_size_option이 남아 있다
// 지금 엔티티로 표를 만든 뒤(ddl-auto create) 그 상태로 되돌려 재현한다. Flyway는 자기 연결로 돌므로 테스트 트랜잭션을 쓰지 않는다
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:product-flyway-align;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// ProductApplication의 @EnableElasticsearchAuditing이 ES 매핑 정보 빈을 찾는다. JPA만 띄우는 테스트라 그 빈만 더한다 (ES에 접속하지 않는다)
@ImportAutoConfiguration(DataElasticsearchAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlywayProductSchemaAlignTest {

	private static final String INSERT_HOLD = "insert into participation_hold "
		+ "(participation_hold_id, held_at, updated_at, expires_at, status, race_course_id, participation_id%s) "
		+ "values (%d, now(), now(), now(), 'HELD', 1, %d%s)";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("옛 칸이 남은 운영 상태의 자리 확보 표에 마이그레이션하면 옛 행이 채워지고, 유니크가 생기고, 옛 칸이 지워진다")
	void alignsLegacyProductTables() {
		// given: 운영과 같은 어긋난 표 (종목 행은 관심 밖이라 참조 검사를 잠시 끄고 넣는다)
		jdbcTemplate.execute("alter table participation_hold drop constraint uk_participation_hold_participation");
		jdbcTemplate.execute("alter table participation_hold add column application_id bigint");
		jdbcTemplate.execute("alter table participation_hold add column crew_application_id bigint");
		jdbcTemplate.execute("create unique index uk_legacy_application on participation_hold (application_id)");
		jdbcTemplate.execute("alter table race_course add column tshirt_size_option varchar(255)");
		jdbcTemplate.execute("set referential_integrity false");
		jdbcTemplate.update(INSERT_HOLD.formatted(", application_id", 1L, 0L, ", 7"));
		jdbcTemplate.update(INSERT_HOLD.formatted("", 2L, 100L, ""));
		jdbcTemplate.execute("set referential_integrity true");

		// when: 운영 기록 표처럼 앞의 SQL은 모두 적용된 것으로 두고, 이 마이그레이션만 돌린다
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_product")
			.baselineOnMigrate(true)
			.baselineVersion("202611181000")
			.load()
			.migrate();

		// then
		assertSoftly(softly -> {
			softly.assertThat(result.success).isTrue();
			softly.assertThat(participationIdOf(1L)).as("옛 행은 application_id 값으로 채워진다").isEqualTo(7L);
			softly.assertThatThrownBy(() -> jdbcTemplate.execute("set referential_integrity false; "
					+ INSERT_HOLD.formatted("", 3L, 100L, "")))
				.as("같은 participation_id로 자리를 두 번 잡을 수 없다")
				.isInstanceOf(DataIntegrityViolationException.class);
			softly.assertThat(columnCount("participation_hold", "application_id")).isZero();
			softly.assertThat(columnCount("participation_hold", "crew_application_id")).isZero();
			softly.assertThat(columnCount("race_course", "tshirt_size_option")).isZero();
		});
	}

	private Long participationIdOf(long holdId) {
		return jdbcTemplate.queryForObject(
			"select participation_id from participation_hold where participation_hold_id = ?", Long.class, holdId);
	}

	private Integer columnCount(String table, String column) {
		return jdbcTemplate.queryForObject("select count(*) from information_schema.columns "
			+ "where table_name = ? and column_name = ?", Integer.class, table, column);
	}
}
