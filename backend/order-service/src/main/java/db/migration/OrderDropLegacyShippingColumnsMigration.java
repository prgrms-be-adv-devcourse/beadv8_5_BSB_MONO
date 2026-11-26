package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 표에 남은 옛 배송 칸 10개를 지운다 (CRU-170)
 *
 * 10/28 BUK-6에서 배송지·키트 배송 값을 배송 표(order_shipment)로 옮기고 엔티티에서만 칸을 뺐다.
 * DB 칸은 다음 배포에서 지우기로 했는데 빠져, 배포마다 스키마 드리프트 알림이 왔다. 코드는 이 칸을 쓰지 않는다
 *
 * 칸이 있을 때만 지운다. V1 기준선은 create table if not exists라 새 DB·테스트 DB에는 이 칸이 처음부터 없다
 * SQL 파일이 아닌 이유: MySQL에는 drop column if exists가 없고, prepare·information_schema 방식은 테스트 H2가 돌리지 못한다
 * (상품 ProductSchemaAlignMigration과 같은 방식). Flyway가 db/migration 위치에서 이 클래스를 찾는다
 */
@Slf4j
public class OrderDropLegacyShippingColumnsMigration implements JavaMigration {

	private static final Map<String, List<String>> LEGACY_COLUMNS = Map.of(
		"order_order", List.of("recipient_name", "recipient_phone", "zipcode", "address", "address_detail"),
		"order_order_item", List.of("kit_delivery_status", "kit_carrier", "kit_tracking_number", "kit_shipped_at",
			"kit_delivered_at"));

	@Override
	public MigrationVersion getVersion() {
		return MigrationVersion.fromVersion("202611261200");
	}

	@Override
	public String getDescription() {
		return "order drop legacy shipping columns";
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
			for (Map.Entry<String, List<String>> entry : LEGACY_COLUMNS.entrySet()) {
				for (String column : entry.getValue()) {
					if (hasColumn(connection, entry.getKey(), column)) {
						statement.execute("alter table " + entry.getKey() + " drop column " + column);
						log.info("{}.{} 칸을 지웠습니다", entry.getKey(), column);
					}
				}
			}
		}
	}

	// 표 이름의 _는 JDBC 이름 패턴에서 아무 글자나 뜻하므로 돌아온 표 이름도 맞춰 본다. 대소문자는 가리지 않는다
	private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
		try (ResultSet rs = connection.getMetaData()
			.getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
			while (rs.next()) {
				if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
					&& column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}
}
