package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

import lombok.extern.slf4j.Slf4j;

/**
 * 운영 상품 표를 코드(엔티티·V1)와 맞춘다 (CRU-88)
 *
 * 왜 필요한가
 * - 11/5 자리 확보 표의 칸 이름을 application_id → participation_id로 바꿨다(f127b10a). 그때 운영은 ddl-auto: update였고,
 *   배포 전에 돌려야 할 이름 바꾸기 SQL이 운영에서 돌지 않았다. Hibernate가 새 칸 participation_id를 따로 더해
 *   옛 행 35개는 0으로 채워졌고, 값이 겹쳐 유니크 인덱스 uk_participation_hold_participation도 생기지 않았다
 * - 11/14 Flyway를 넣었지만 V1은 create table if not exists라 이미 있던 표를 건너뛰어, 빠진 인덱스·외래 키가 그대로 남았다
 *   (11/14 bigint 장애 V202611141600과 같은 원인)
 * - 유니크가 없어 결제 판매의 findForUpdate(participationId)가 표 전체를 훑으며 모든 행을 잠갔다.
 *   자리 확보(INSERT)와 서로 기다려 교착이 났다 (11/20 운영 부하 테스트 11건, CRU-84)
 *
 * 하는 일 (모두 「있을 때만·없을 때만」이라, 이미 맞는 새 DB·로컬·테스트 H2에서는 아무것도 바꾸지 않는다)
 * 1. 옛 칸 application_id가 있으면, participation_id가 0인 옛 행을 application_id 값으로 채운다
 * 2. participation_id에 유니크 인덱스가 없으면 만든다. 그 전에 같은 값이 둘 이상인지 세어, 있으면 멈춘다
 * 3. 옛 칸 application_id(옛 유니크와 함께 지워진다)·crew_application_id를 지운다
 * 4. product_kit_option의 대회 외래 키가 없으면 만든다. 대회가 없는 키트 옵션이 있으면 멈춘다
 * 5. 11/9 코드에서 지운 race_course.tshirt_size_option이 남아 있으면 지운다 (옛 값은 배포 전 백업에 있다)
 *
 * SQL 파일이 아닌 이유: 「있을 때만」을 MySQL SQL로 쓰려면 prepare·information_schema가 필요한데,
 * 테스트의 H2(MySQL 호환 모드)는 이를 돌리지 못한다. JDBC 표 정보로 판단하면 MySQL·H2 모두에서 같게 돈다
 * Flyway가 db/migration 위치에서 이 클래스를 찾는다. 버전은 클래스 이름이 아니라 getVersion()으로 준다
 */
@Slf4j
public class ProductSchemaAlignMigration implements JavaMigration {

	private static final String HOLD = "participation_hold";
	private static final String KIT_OPTION = "product_kit_option";
	private static final String RACE_COURSE = "race_course";

	@Override
	public MigrationVersion getVersion() {
		return MigrationVersion.fromVersion("202611201200");
	}

	@Override
	public String getDescription() {
		return "product schema align participation hold unique";
	}

	@Override
	public Integer getChecksum() {
		return null;
	}

	@Override
	public boolean canExecuteInTransaction() {
		return true;
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		try (Statement statement = connection.createStatement()) {
			// 1. 옛 행의 신청 ID 채우기
			if (hasColumn(connection, HOLD, "application_id")) {
				int filled = statement.executeUpdate("update participation_hold set participation_id = application_id "
					+ "where participation_id = 0 and application_id is not null");
				log.info("자리 확보 옛 행 {}개의 participation_id를 application_id로 채웠습니다", filled);
			}

			// 2. 신청 하나에 자리 하나 (판매·확보가 행 하나만 잠그게 한다)
			if (!hasUniqueIndexOn(connection, HOLD, "participation_id")) {
				long duplicated = count(statement, "select count(*) from (select participation_id "
					+ "from participation_hold group by participation_id having count(*) > 1) d");
				if (duplicated > 0) {
					throw new IllegalStateException("participation_hold에 같은 participation_id가 둘 이상인 값이 "
						+ duplicated + "개 있어 유니크 인덱스를 만들 수 없습니다. 데이터를 먼저 확인하세요");
				}
				statement.execute("create unique index uk_participation_hold_participation "
					+ "on participation_hold (participation_id)");
				log.info("participation_hold에 유니크 인덱스 uk_participation_hold_participation을 만들었습니다");
			}

			// 3. 옛 칸 지우기
			dropColumnIfExists(connection, statement, HOLD, "application_id");
			dropColumnIfExists(connection, statement, HOLD, "crew_application_id");

			// 4. 키트 옵션 → 대회 외래 키
			if (!hasForeignKey(connection, KIT_OPTION, "race_id", "race")) {
				long orphans = count(statement, "select count(*) from product_kit_option k "
					+ "left join race r on r.race_id = k.race_id where r.race_id is null");
				if (orphans > 0) {
					throw new IllegalStateException("대회가 없는 키트 옵션이 " + orphans + "개 있어 외래 키를 만들 수 없습니다");
				}
				statement.execute("alter table product_kit_option add constraint FKei68vy79h0dm4se3brqcqkmo6 "
					+ "foreign key (race_id) references race (race_id)");
				log.info("product_kit_option에 대회 외래 키를 만들었습니다");
			}

			// 5. 키트 옵션으로 옮긴 종목 티셔츠 사이즈 칸 (1d646eb7)
			dropColumnIfExists(connection, statement, RACE_COURSE, "tshirt_size_option");
		}
	}

	private static void dropColumnIfExists(Connection connection, Statement statement, String table, String column)
		throws SQLException {
		if (hasColumn(connection, table, column)) {
			statement.execute("alter table " + table + " drop column " + column);
			log.info("{}.{} 칸을 지웠습니다", table, column);
		}
	}

	private static long count(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	// MySQL은 표·칸 이름을 소문자로 돌려주고, 테스트 H2는 DATABASE_TO_LOWER로 소문자다. 그래도 대소문자는 가리지 않고 비교한다
	private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
		DatabaseMetaData meta = metaData(connection);
		try (ResultSet rs = meta.getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
			while (rs.next()) {
				if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}

	// 인덱스 이름은 DB마다 다르게 붙을 수 있어(H2는 제약 이름과 다른 이름을 쓴다) 이름이 아니라 「그 칸 하나로 된 유니크」인지 본다
	private static boolean hasUniqueIndexOn(Connection connection, String table, String column) throws SQLException {
		Map<String, List<String>> columnsByIndex = new HashMap<>();
		try (ResultSet rs = metaData(connection).getIndexInfo(connection.getCatalog(), connection.getSchema(), table,
			true, false)) {
			while (rs.next()) {
				String index = rs.getString("INDEX_NAME");
				String indexColumn = rs.getString("COLUMN_NAME");
				if (index != null && indexColumn != null) {
					columnsByIndex.computeIfAbsent(index, key -> new ArrayList<>())
						.add(indexColumn.toLowerCase(Locale.ROOT));
				}
			}
		}
		return columnsByIndex.values().stream().anyMatch(columns -> columns.equals(List.of(column)));
	}

	private static boolean hasForeignKey(Connection connection, String table, String column, String referencedTable)
		throws SQLException {
		DatabaseMetaData meta = metaData(connection);
		try (ResultSet rs = meta.getImportedKeys(connection.getCatalog(), connection.getSchema(), table)) {
			while (rs.next()) {
				if (column.equalsIgnoreCase(rs.getString("FKCOLUMN_NAME"))
					&& referencedTable.equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}

	private static DatabaseMetaData metaData(Connection connection) throws SQLException {
		return connection.getMetaData();
	}
}
