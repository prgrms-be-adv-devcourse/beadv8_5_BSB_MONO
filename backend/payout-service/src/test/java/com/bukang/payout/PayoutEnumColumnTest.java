package com.bukang.payout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;

/**
 * 정산의 enum 칸은 DB ENUM이다 (PR #265 리뷰 REV-4, 11/2 결정). 코드에 enum 값을 더하고 ENUM 목록을 바꾸는 Flyway SQL을 빠뜨리면
 * 테스트 · 배포는 통과하고 운영에서 그 값을 처음 저장할 때 실패한다. Hibernate validate도 ENUM 목록은 보지 않는다
 * 그래서 Flyway SQL을 모두 돌린 표의 ENUM 목록이 엔티티의 enum 값과 같은지 칸마다 확인한다 (사람이 빠뜨려도 CI에서 먼저 실패하게)
 * 칸은 엔티티에서 찾으므로 enum 칸을 새로 만들어도 이 테스트를 고칠 필요가 없다
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:payout-enum-columns;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=true",
	"spring.flyway.table=flyway_schema_history_payout",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
class PayoutEnumColumnTest {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Flyway SQL을 모두 돌린 표의 ENUM 목록이 엔티티의 enum 값과 칸마다 같다")
	void enumColumnsMatchCode() {
		List<EnumColumn> columns = enumColumns();
		// 회수 상태 · 청구 상태 · 원장 계정 · 줄 종류 등 10칸 (11/2 기준). 0이면 칸을 찾는 방법이 깨진 것이다
		assertThat(columns).hasSizeGreaterThanOrEqualTo(10);

		for (EnumColumn column : columns) {
			List<String> dbValues = jdbcTemplate.queryForList("""
					select e.value_name from information_schema.columns c
					join information_schema.enum_values e
						on e.object_schema = c.table_schema and e.object_name = c.table_name
						and e.object_type = 'TABLE' and e.enum_identifier = c.dtd_identifier
					where c.table_name = ? and c.column_name = ?""",
				String.class, column.table(), column.column());

			assertThat(dbValues)
				.as("%s.%s의 ENUM 목록. enum 값을 바꿨다면 ENUM 목록을 바꾸는 Flyway SQL을 더한다", column.table(), column.column())
				.containsExactlyInAnyOrderElementsOf(column.codeValues());
		}
	}

	private List<EnumColumn> enumColumns() {
		List<EnumColumn> columns = new ArrayList<>();
		for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
			String table = entity.getJavaType().getAnnotation(Table.class).name().toLowerCase();
			for (Attribute<?, ?> attribute : entity.getAttributes()) {
				Class<?> type = attribute.getJavaType();
				if (type.isEnum()) {
					List<String> values = Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>)value).name())
						.toList();
					columns.add(new EnumColumn(table, snakeCase(attribute.getName()), values));
				}
			}
		}
		return columns;
	}

	// 칸 이름은 Spring 기본 이름 규칙(필드 이름을 snake_case로)을 따른다
	private static String snakeCase(String fieldName) {
		return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

	private record EnumColumn(String table, String column, List<String> codeValues) {
	}
}
