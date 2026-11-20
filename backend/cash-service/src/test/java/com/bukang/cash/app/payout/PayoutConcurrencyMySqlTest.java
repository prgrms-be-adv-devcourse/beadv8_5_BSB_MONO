package com.bukang.cash.app.payout;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.cash.TestcontainersConfiguration;

/**
 * PayoutConcurrencyTest를 MySQL에서 다시 돈다. H2는 READ COMMITTED라 잠금을 기다린 뒤 앞 요청이 저장한 기록이 보이지만,
 * MySQL은 REPEATABLE READ라 트랜잭션의 첫 일반 조회 때 잡은 읽기 시점으로 읽는다.
 * 그래서 잠그기 전에 일반 조회를 하면 잠금을 얻은 뒤에도 앞 요청의 이동 기록이 안 보여 같은 기록을 또 넣다가 유니크 키에 걸린다(500)
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
class PayoutConcurrencyMySqlTest extends PayoutConcurrencyTest {
}
