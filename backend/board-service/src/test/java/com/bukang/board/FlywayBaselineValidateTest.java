package com.bukang.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 빈 DB에 Flyway로 모든 SQL을 돌린 뒤 Hibernate validate로 엔티티와 표가 맞는지 확인한다
// 엔티티를 고치고 SQL을 빠뜨리면 여기서 실패한다. H2(MySQL 호환 모드)라 실제 MySQL과 완전히 같지는 않다
// 다른 테스트와 섞이지 않게 DB 이름을 따로 쓴다
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:board-flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=true",
	// dev·운영과 같은 Flyway 설정
	"spring.flyway.table=flyway_schema_history_board",
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
	@MockitoBean
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("모든 SQL을 적용한 표가 엔티티와 맞는다 (Hibernate validate 통과)")
	void migrationsMatchEntities() {
		// validate에 실패하면 컨텍스트가 뜨지 않아 이 테스트까지 오지 못한다. 여기서는 남은 SQL이 없는지만 본다
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current()).isNotNull();
	}

	@Test
	@DisplayName("좋아요는 (글, 회원) 유니크 제약이 있어 같은 좋아요를 두 번 넣지 못한다")
	void likeIsUniquePerPostAndMember() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		try {
			insertLike(jdbc, 1, 1);
			insertLike(jdbc, 1, 2);

			assertThatThrownBy(() -> insertLike(jdbc, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbc.update("delete from board_post_like");
		}
	}

	@Test
	@DisplayName("인박스는 (컨슈머 그룹, 메시지 ID) 유니크 제약이 있다")
	void inboxIsUniquePerGroupAndMessage() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		try {
			insertInbox(jdbc, "board", "m-1");
			insertInbox(jdbc, "other", "m-1");

			assertThatThrownBy(() -> insertInbox(jdbc, "board", "m-1"))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbc.update("delete from board_inbox_kafka_event");
		}
	}

	private static void insertLike(JdbcTemplate jdbc, long postId, long memberId) {
		jdbc.update("insert into board_post_like (post_id, member_id) values (?, ?)", postId, memberId);
	}

	private static void insertInbox(JdbcTemplate jdbc, String group, String messageId) {
		jdbc.update("insert into board_inbox_kafka_event (message_id, consumer_group, topic, processed_date) "
			+ "values (?, ?, 'queuing.member.joined', current_timestamp)", messageId, group);
	}
}
