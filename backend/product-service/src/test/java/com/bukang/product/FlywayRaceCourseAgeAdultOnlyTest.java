package com.bukang.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// 나이 정리 SQL(CRU-54)이 MySQL 호환 모드 H2에서 돌고, 기존 종목 값을 14·19로 바꾸는지 확인한다
// 이 SQL은 min_age·max_age 두 칸만 쓰므로 그 두 칸만 가진 표로 확인한다
class FlywayRaceCourseAgeAdultOnlyTest {

	@Test
	@DisplayName("최소 나이 16은 14로, 19 이상(25)은 19로 바꾸고 최대 나이는 모두 비운다")
	void normalizesExistingAges() throws Exception {
		JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
			"jdbc:h2:mem:product-age-adult-only;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
		jdbc.execute("create table race_course (race_course_id bigint primary key, min_age integer not null, "
			+ "max_age integer)");
		jdbc.update("insert into race_course values (1, 16, 70), (2, 25, null), (3, 14, null), (4, 19, 60), "
			+ "(5, 18, null)");

		String sql = new ClassPathResource("db/migration/V202611181000__race_course_age_adult_only.sql")
			.getContentAsString(StandardCharsets.UTF_8);
		for (String statement : sql.split(";")) {
			if (!statement.replaceAll("(?m)^\\s*--.*$", "").isBlank()) {
				jdbc.execute(statement);
			}
		}

		List<Map<String, Object>> rows = jdbc.queryForList(
			"select race_course_id, min_age, max_age from race_course order by race_course_id");
		assertThat(rows).extracting(row -> row.get("min_age")).containsExactly(14, 19, 14, 19, 14);
		assertThat(rows).extracting(row -> row.get("max_age")).containsOnlyNulls();
	}
}
