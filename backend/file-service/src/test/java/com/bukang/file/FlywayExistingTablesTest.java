package com.bukang.file;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Flyway를 넣기 전 서버·로컬 DB처럼 Hibernate(ddl-auto: update)가 이미 파일 표를 만든 DB에서 처음 Flyway가 도는 경우 (BUK-173)
// V1은 create table if not exists라 표와 데이터를 그대로 두고, 기록 표에 "적용함"만 남겨야 한다
// 인덱스·제약을 create table 밖(create index, alter table)에 두면 여기서 "이미 있음"으로 실패한다
// Flyway는 자기 연결로 SQL을 돌리므로, 테스트 트랜잭션 없이 넣은 행을 커밋해 둔다
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:file-flyway-existing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlywayExistingTablesTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("파일 표가 이미 있는 DB에서도 V1이 실패 없이 돌고, 데이터는 그대로다")
	void keepsExistingTables() {
		// given
		jdbcTemplate.update("insert into file_inbox_kafka_event (consumer_group, message_id, processed_date, topic)"
			+ " values ('file', 'm-1', current_timestamp, 'queuing.member.joined')");

		// when: dev·운영과 같은 Flyway 설정
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_file")
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.outOfOrder(true)
			.load()
			.migrate();

		// then
		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isPositive();
		assertThat(jdbcTemplate.queryForObject("select count(*) from file_inbox_kafka_event", Integer.class))
			.isEqualTo(1);
	}
}
