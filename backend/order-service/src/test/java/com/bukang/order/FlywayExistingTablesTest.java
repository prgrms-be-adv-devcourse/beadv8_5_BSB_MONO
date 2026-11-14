package com.bukang.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
	private static final String OUTBOX_MESSAGE_ID_FORMAT =
		"order-outbox-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("주문 표가 이미 있는 DB에서도 V1이 실패 없이 돌고, 표와 데이터는 바뀌지 않는다")
	void keepsExistingTables() {
		jdbcTemplate.update("insert into order_cart (member_id) values (1)");
		// 서버 표는 칸 이름을 바꾸기 전(crew_application_id) 상태다. Hibernate는 지금 엔티티(party_id)로 만들므로 되돌려 둔다
		jdbcTemplate.execute("alter table order_participation alter column party_id rename to crew_application_id");
		// 서버에는 파티 표가 아직 없다(BUK-35 SQL이 만든다)
		jdbcTemplate.execute("drop table order_party_member");
		jdbcTemplate.execute("drop table order_party");
		// 서버 아웃박스 표에는 message_id 칸이 아직 없고(BUK-81 SQL이 더한다), 보내지 않은 행이 남아 있을 수 있다
		jdbcTemplate.execute("alter table order_outbox_kafka_event drop constraint uk_order_outbox_kafka_message_id");
		jdbcTemplate.execute("alter table order_outbox_kafka_event drop column message_id");
		for (String status : List.of("PENDING", "SEND_FAILED")) {
			jdbcTemplate.update("insert into order_outbox_kafka_event (ref_type, ref_id, event_type, payload, status,"
				+ " retry_count) values ('Participation', 1, 'topic', '{}', ?, 0)", status);
		}
		// 서버 표에는 아직 배송 조회 칸·이동 기록 표가 없다 (BUK-55)
		jdbcTemplate.execute("drop table order_shipment_tracking_event");
		jdbcTemplate.execute("drop index idx_shipment_tracking");
		for (String column : List.of("carrier_id", "tracking_status", "tracking_status_text", "last_event_at",
			"last_tracked_at", "next_tracking_at", "tracking_started_at")) {
			jdbcTemplate.execute("alter table order_shipment drop column " + column);
		}
		// 서버 표에는 아직 취소 사유 칸이 없다 (BUK-74)
		jdbcTemplate.execute("alter table order_order_item drop column cancel_reason");
		// 서버 표에는 아직 키트 수령 확인 칸이 없다 (BUK-225)
		jdbcTemplate.execute("alter table order_shipment drop column received_at");

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
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from information_schema.columns"
				+ " where table_name = 'order_participation' and column_name = 'party_id'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from information_schema.table_constraints"
				+ " where table_name = 'order_participation' and constraint_name = 'fk_participation_party'",
			Integer.class)).isEqualTo(1);
		// 이미 있던 아웃박스 행은 행마다 다른 order-outbox-{UUID}로 채워진다
		assertThat(jdbcTemplate.queryForList("select message_id from order_outbox_kafka_event", String.class))
			.hasSize(2)
			.doesNotHaveDuplicates()
			.allMatch(messageId -> messageId.matches(OUTBOX_MESSAGE_ID_FORMAT));
	}
}
