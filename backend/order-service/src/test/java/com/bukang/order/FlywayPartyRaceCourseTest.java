package com.bukang.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// 파티 종목 칸(V202611051200)을 더할 때 이미 있는 파티 줄을 파티장 신청서 종목으로 채운다 (BUK-36, 11/05)
class FlywayPartyRaceCourseTest {

	@Test
	@DisplayName("이미 있는 파티 줄은 파티장 신청서의 종목, 파티장이 넘어갔으면 남은 신청서의 종목으로 채워진다")
	void fillsExistingParties() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
			"jdbc:h2:mem:order-flyway-party-course;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource).target("202611051100").load().migrate();

		// 파티 1: 파티장(3) 신청서 종목 10. 파티 2: 파티장(9)이 신청서 없이 넘겨받음, 남은 신청서 종목 11
		for (long partyId : new long[] {1, 2}) {
			jdbc.update("insert into order_party (id, crew_id, leader_member_id, race_id, join_token, "
				+ "join_token_issued_at, status, version) values (?, 7, ?, 100, ?, now(), 'RECRUITING', 0)",
				partyId, partyId == 1 ? 3 : 9, "token-" + partyId);
		}
		insertParticipation(jdbc, 3, 10, 1);
		insertParticipation(jdbc, 4, 10, 1);
		insertParticipation(jdbc, 5, 11, 2);

		flyway(dataSource).load().migrate();

		assertThat(jdbc.queryForList("select race_course_id from order_party order by id", Long.class))
			.containsExactly(10L, 11L);
	}

	private static void insertParticipation(JdbcTemplate jdbc, long memberId, long raceCourseId, long partyId) {
		jdbc.update("insert into order_participation (member_id, race_course_id, race_id, party_id, "
			+ "participation_type, status) values (?, ?, 100, ?, 'PARTY', 'PENDING')", memberId, raceCourseId, partyId);
	}

	private static FluentConfiguration flyway(DriverManagerDataSource dataSource) {
		return Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_order")
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.outOfOrder(true);
	}
}
