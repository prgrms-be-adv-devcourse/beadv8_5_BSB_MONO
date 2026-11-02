package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

/**
 * 상태 칸은 DB의 ENUM이 아니라 글자 칸이다 (PR #265 리뷰, 11/2 결정)
 * ENUM 칸이면 상태 값을 새로 만들 때(회수 CANCELED 등) 기존 DB가 받지 못하고, ddl-auto update도 고쳐 주지 않는다
 * 값은 코드의 enum으로만 읽고 쓴다. DB 검사 규칙(CHECK)은 걸지 않는다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutStatusColumnTest {

	@Autowired
	private EntityManager entityManager;

	@ParameterizedTest(name = "{0}.{1}")
	@CsvSource({"payout_payout_recovery, status", "payout_payout_claim, status"})
	@DisplayName("TC-REV4-1 회수 · 청구 상태 칸은 DB ENUM이 아닌 글자 칸이다")
	void statusColumnIsVarchar(String table, String column) {
		Object dataType = entityManager.createNativeQuery("""
				select data_type from information_schema.columns
				where lower(table_name) = :table and lower(column_name) = :column""")
			.setParameter("table", table)
			.setParameter("column", column)
			.getSingleResult();

		assertThat(dataType.toString()).isIn("CHARACTER VARYING", "VARCHAR");
	}

	@Test
	@DisplayName("TC-REV4-1 상계로 끝난 회수를 대회 취소로 되돌리면 CANCELED로 저장되고 다시 읽어도 CANCELED다")
	void canceledRecoveryIsSavedAndReadBack() {
		PayoutRecovery recovery = PayoutRecovery.scheduleChanged(1, 7, 108, 20_000,
			LocalDateTime.of(2026, 10, 30, 10, 0));
		entityManager.persist(recovery);
		recovery.completeByOffset(LocalDateTime.of(2026, 11, 1, 3, 0));
		recovery.cancelByRaceCancel();
		entityManager.flush();
		entityManager.clear();

		Object saved = entityManager.createNativeQuery("select status from payout_payout_recovery where id = :id")
			.setParameter("id", recovery.getId())
			.getSingleResult();
		assertThat(saved.toString()).isEqualTo("CANCELED");
		assertThat(entityManager.find(PayoutRecovery.class, recovery.getId()).getStatus())
			.isEqualTo(PayoutRecoveryStatus.CANCELED);
	}
}
