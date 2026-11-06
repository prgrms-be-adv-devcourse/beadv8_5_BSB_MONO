package com.bukang.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// 빈 DB에 Flyway로 모든 SQL(V1 기준선 + 이후 변경)을 돌린 뒤 Hibernate validate로 엔티티와 표가 맞는지 확인한다 (BE-024)
// H2(MySQL 호환 모드)라 실제 MySQL과 완전히 같지는 않다. 다른 테스트와 섞이지 않게 DB 이름을 따로 쓴다
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=true",
	// dev·운영과 같은 Flyway 설정 (BE-024)
	"spring.flyway.table=flyway_schema_history_member",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class FlywayBaselineValidateTest {

	@Autowired
	private Flyway flyway;
	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("모든 SQL을 적용한 표가 엔티티와 맞는다 (Hibernate validate 통과)")
	void migrationsMatchEntities() {
		// validate에 실패하면 컨텍스트가 뜨지 않아 이 테스트까지 오지 못한다. 여기서는 남은 SQL이 없는지만 본다
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current()).isNotNull();
	}

	@Test
	@DisplayName("아웃박스 message_id는 비어 있는 행(컬럼을 더하기 전에 만든 행)은 여러 개 둘 수 있고, 같은 값은 두 번 넣지 못한다")
	void outboxMessageIdIsUniqueButNullable() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		try {
			insertOutbox(jdbc, "00000000-0000-0000-0000-000000000001", null);
			insertOutbox(jdbc, "00000000-0000-0000-0000-000000000002", null);
			insertOutbox(jdbc, "00000000-0000-0000-0000-000000000003", "member-outbox-same");

			assertThatThrownBy(() -> insertOutbox(jdbc, "00000000-0000-0000-0000-000000000004", "member-outbox-same"))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbc.update("delete from member_outbox_event");
		}
	}

	private static void insertOutbox(JdbcTemplate jdbc, String eventId, String messageId) {
		jdbc.update("insert into member_outbox_event (event_id, message_id, topic, ref_type, ref_id, payload, "
				+ "occurred_at, status, retry_count) values (?, ?, 'queuing.member.joined', 'Member', 1, '{}', "
				+ "current_timestamp, 'PENDING', 0)", eventId, messageId);
	}
}
