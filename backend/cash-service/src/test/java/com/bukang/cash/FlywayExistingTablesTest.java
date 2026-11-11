package com.bukang.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Flyway를 넣기 전 서버·로컬 DB처럼 Hibernate(ddl-auto: update)가 이미 cash 표를 만든 MySQL에서 처음 Flyway가 도는 경우
// V1은 표가 있으면 건너뛰므로, update가 바꾸지 못한 ENUM 목록·NULL 허용·칸 타입(int→bigint)·길이는 다음 SQL이 맞춰야 한다
// 인덱스·제약을 create table 밖에 두면 여기서 "이미 있음"으로 실패한다
@SpringBootTest(properties = {
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FlywayExistingTablesTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("옛 ENUM 목록·NOT NULL 은행 칸이 있는 DB에서 Flyway가 돌면, 데이터는 남고 새 값을 저장할 수 있다")
	void migratesExistingTables() {
		// 운영 표는 새 enum 값을 더하기 전에 만들어졌다 (update는 ENUM 목록과 NULL 허용을 바꾸지 않는다)
		jdbcTemplate.execute("alter table cash_wallet_charge modify status"
			+ " enum('CANCELED','COMPLETED','FAILED','IN_PROGRESS','REQUESTED') not null");
		jdbcTemplate.execute("alter table cash_wallet_ledger modify ledger_type enum('사용__주문결제','임시보관__주문결제',"
			+ "'임시보관반환__주문취소','정산수령__상품판매_대금','정산수령__상품판매_수수료','정산지급__상품판매_대금',"
			+ "'정산지급__상품판매_수수료','출금__통장입금','출금취소__통장입금','충전__PG결제_토스페이먼츠','환불__주문취소') not null");
		jdbcTemplate.execute("alter table cash_wallet_withdrawal modify status"
			+ " enum('COMPLETED','FAILED','REQUESTED') not null");
		jdbcTemplate.execute("alter table cash_wallet_withdrawal modify bank_code varchar(10) not null");
		jdbcTemplate.execute("alter table cash_wallet_withdrawal modify account_number varchar(512) not null");
		jdbcTemplate.execute("alter table cash_wallet_withdrawal modify account_holder varchar(50) not null");
		// ID·금액을 long으로 바꾸기 전에 만든 운영 표는 int 칸이다 (update는 칸 타입을 넓히지 않는다).
		// 환급 몫·회수 입금 표는 그 뒤에 생겨 처음부터 bigint다. 암호문 칸은 512로 늘리기 전 길이(255)다
		// MySQL은 외래 키가 걸린 칸의 타입을 바꾸지 못해, 외래 키를 풀고 바꾼 뒤 같은 이름으로 다시 건다
		List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
			select table_name t, constraint_name k, column_name c, referenced_table_name rt, referenced_column_name rc
			from information_schema.key_column_usage
			where table_schema = database() and table_name like 'cash\\_%' and referenced_table_name is not null""");
		for (Map<String, Object> key : foreignKeys) {
			jdbcTemplate.execute("alter table " + key.get("t") + " drop foreign key " + key.get("k"));
		}
		for (Map<String, Object> column : jdbcTemplate.queryForList("""
			select table_name t, column_name c, is_nullable n, extra e from information_schema.columns
			where table_schema = database() and data_type = 'bigint'
				and table_name like 'cash\\_%'
				and table_name not in ('cash_wallet_withdrawal_item', 'cash_recovery_deposit')""")) {
			jdbcTemplate.execute("alter table " + column.get("t") + " modify " + column.get("c") + " int"
				+ ("NO".equals(column.get("n")) ? " not null " : " ") + column.get("e"));
		}
		// 환급 몫 표는 bigint 칸이 int ID를 가리키게 되어 update가 외래 키를 만들지 못한다. 그 상태 그대로 둔다
		for (Map<String, Object> key : foreignKeys) {
			if (!"cash_wallet_withdrawal_item".equals(key.get("t"))) {
				jdbcTemplate.execute("alter table " + key.get("t") + " add constraint " + key.get("k")
					+ " foreign key (" + key.get("c") + ") references " + key.get("rt") + " (" + key.get("rc") + ")");
			}
		}
		jdbcTemplate.execute("alter table cash_wallet_charge modify pg_payment_key varchar(255)");
		jdbcTemplate.execute("alter table cash_wallet_withdrawal modify account_number varchar(255) not null");

		jdbcTemplate.update("insert into cash_member (id, nickname) values (10, '러너')");
		jdbcTemplate.update("insert into cash_wallet (id, holder_id, balance, version) values (10, 10, 5000, 0)");
		jdbcTemplate.update("insert into cash_wallet_charge (id, wallet_id, amount, canceled_amount, review_flagged,"
			+ " pg_provider, idempotency_key, status) values (1, 10, 5000, 0, 0, 'TOSS', 'charge-1', 'COMPLETED')");
		jdbcTemplate.update("insert into cash_wallet_withdrawal (id, wallet_id, amount, bank_code, account_number,"
			+ " account_holder, idempotency_key, status)"
			+ " values (1, 10, 1000, '004', 'enc', '러너', 'wd-1', 'COMPLETED')");

		// dev·운영과 같은 Flyway 설정
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.table("flyway_schema_history_cash")
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.outOfOrder(true)
			.load()
			.migrate();

		assertThat(result.success).isTrue();
		assertThat(jdbcTemplate.queryForObject("select concat(c.status, ':', w.status, ':', w.bank_code)"
			+ " from cash_wallet_charge c, cash_wallet_withdrawal w", String.class))
			.isEqualTo("COMPLETED:COMPLETED:004");

		jdbcTemplate.update("update cash_wallet_charge set status = 'UNCERTAIN' where id = 1");
		jdbcTemplate.update("insert into cash_wallet_withdrawal (wallet_id, amount, idempotency_key, status)"
			+ " values (10, 1000, 'wd-2', 'IN_PROGRESS')");
		jdbcTemplate.update("insert into cash_wallet_ledger (wallet_id, holder_id, amount, balance_after, ref_id,"
			+ " ref_type_code, ledger_type) values (10, 10, -1000, 4000, 2, 'Withdrawal', '출금__PG결제취소_토스페이먼츠')");
		assertThat(jdbcTemplate.queryForObject("select status from cash_wallet_charge where id = 1", String.class))
			.isEqualTo("UNCERTAIN");

		// int 칸이 남으면 validate로 바꿀 때 "found [int], but expecting [bigint]"로 앱이 뜨지 않는다
		assertThat(jdbcTemplate.queryForList("""
			select concat(table_name, '.', column_name) from information_schema.columns
			where table_schema = database() and table_name like 'cash\\_%' and data_type = 'int'""", String.class))
			.isEmpty();
		assertThat(jdbcTemplate.queryForList("""
			select character_maximum_length from information_schema.columns where table_schema = database()
				and table_name in ('cash_wallet_charge', 'cash_wallet_withdrawal')
				and column_name in ('pg_payment_key', 'account_number')""", Long.class))
			.containsExactly(512L, 512L);
		// 외래 키 9개가 모두 걸려 있다 (위에서 빼 둔 환급 몫 표의 2개도 SQL이 새로 건다)
		assertThat(jdbcTemplate.queryForObject("""
			select count(*) from information_schema.table_constraints where table_schema = database()
				and table_name like 'cash\\_%' and constraint_type = 'FOREIGN KEY'""", Integer.class))
			.isEqualTo(foreignKeys.size());
	}
}
