package com.bukang.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;

/**
 * cash의 enum 칸은 MySQL ENUM이다. 코드에 enum 값을 더하고 ENUM 목록을 바꾸는 Flyway SQL을 빠뜨리면
 * 테스트·배포는 통과하고 운영에서 그 값을 처음 저장할 때 실패한다. Hibernate validate도 ENUM 목록은 보지 않는다
 * 그래서 Flyway SQL을 모두 돌린 표의 ENUM 목록이 엔티티의 enum 값과 같은지 칸마다 확인한다
 * 칸은 엔티티에서 찾으므로 enum 칸을 새로 만들어도 이 테스트를 고칠 필요가 없다
 * 설정을 FlywayValidateTest와 똑같이 둬 같은 컨텍스트(MySQL 컨테이너)를 함께 쓴다
 */
@SpringBootTest(properties = {
	"spring.flyway.enabled=true",
	"spring.flyway.table=flyway_schema_history_cash",
	"spring.flyway.baseline-on-migrate=true",
	"spring.flyway.baseline-version=0",
	"spring.flyway.out-of-order=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CashEnumColumnTest {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Flyway SQL을 모두 돌린 표의 ENUM 목록이 엔티티의 enum 값과 칸마다 같다")
	void enumColumnsMatchCode() {
		List<EnumColumn> columns = enumColumns();
		// 충전·결제·환급·환급 몫 상태, 원장 종류 등 9칸 (11/10 기준). 0이면 칸을 찾는 방법이 깨진 것이다
		assertThat(columns).hasSizeGreaterThanOrEqualTo(9);

		for (EnumColumn column : columns) {
			String columnType = jdbcTemplate.queryForObject("""
					select column_type from information_schema.columns
					where table_schema = database() and table_name = ? and column_name = ?""",
				String.class, column.table(), column.column());

			assertThat(enumValues(columnType))
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

	// MySQL column_type은 enum('A','B') 모양이다. ENUM이 아니면(varchar 등) 빈 목록이 되어 위에서 실패한다
	private static List<String> enumValues(String columnType) {
		if (columnType == null || !columnType.startsWith("enum('")) {
			return List.of();
		}
		String body = columnType.substring("enum('".length(), columnType.length() - "')".length());
		return List.of(body.split("','"));
	}

	// 칸 이름은 Spring 기본 이름 규칙(필드 이름을 snake_case로)을 따른다
	private static String snakeCase(String fieldName) {
		return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

	private record EnumColumn(String table, String column, List<String> codeValues) {
	}
}
