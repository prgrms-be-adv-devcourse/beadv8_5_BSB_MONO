package com.bukang.cash.app.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.TestcontainersConfiguration;
import com.bukang.cash.app.payment.CashRefundableChargeQueryUseCase;
import com.bukang.cash.domain.charge.ChargeStatus;
import com.bukang.cash.domain.charge.WalletCharge;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.payment.RefundableChargeDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.wallet.WalletRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * 기한 알림 배치가 지갑·충전을 읽는 방법(IN 두 번 / fetch join)과 묶음 크기(1,000·2,000·3,000)를 함께 MySQL에서 잰다.
 * 판정 기준은 docs/troubleshooting/2026-11-17-기한-알림-배치-SQL-줄이기.md에 재기 전에 적었다.
 * 실행할 때만 켠다(CASH_MEASURE=true, Docker 필요). 결과는 build/measure/에 남긴다.
 * - 배치 전체가 아니라 묶음마다 읽고 남은 금액을 계산하는 부분만 잰다. 방법과 묶음 크기가 바꾸는 건 이 부분뿐이다
 * - 시간과 메모리는 따로 잰다. 메모리를 재려고 부르는 GC가 시간을 흔들기 때문이다
 */
@SpringBootTest(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true",
	"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CASH_MEASURE", matches = "true")
class CashCardCancelDeadlineNoticeReadMeasureTest {
	private static final int[] CHARGES_PER_MEMBER = {1, 10, 30};
	// 1만 명 표에서 대상 1만 명은 IN 목록이 표 전체라 어느 조합이든 표를 다 읽어 비교할 것이 없어 뺀다
	private static final Map<Integer, List<Integer>> TARGETS_BY_TABLE = new LinkedHashMap<>();

	static {
		TARGETS_BY_TABLE.put(10_000, List.of(1_000, 3_000));
		TARGETS_BY_TABLE.put(100_000, List.of(1_000, 3_000, 10_000));
	}

	private static final int WARMUP = 3;
	private static final int ROUNDS = 7;
	private static final int MEMORY_ROUNDS = 3;
	private static final long SEED = 42;
	private static final LocalDateTime NOW = LocalDateTime.of(2027, 10, 6, 9, 0);
	// 결제취소 마지막 날(승인일 + 1년 − 1일)이 NOW의 정확히 3일 뒤인 승인 시각
	private static final LocalDateTime DEADLINE_APPROVED = LocalDateTime.of(2026, 10, 10, 9, 0);
	private static final String FETCH_JOIN = "select c from WalletCharge c join fetch c.wallet "
		+ "where c.wallet.id in :ids and c.status = :status";
	private static final int INSERT_ROWS = 5_000;

	enum Method {
		IN_TWICE("IN 두 번"),
		FETCH_JOIN("fetch join");

		private final String label;

		Method(String label) {
			this.label = label;
		}
	}

	record Combo(Method method, int chunk) {
		String label() {
			return method.label + " " + String.format(Locale.ROOT, "%,d", chunk);
		}
	}

	// 기준선(지금 코드)이 맨 앞이다
	private static final List<Combo> COMBOS = List.of(
		new Combo(Method.IN_TWICE, 1_000), new Combo(Method.IN_TWICE, 2_000), new Combo(Method.IN_TWICE, 3_000),
		new Combo(Method.FETCH_JOIN, 1_000), new Combo(Method.FETCH_JOIN, 2_000), new Combo(Method.FETCH_JOIN, 3_000));

	record Timing(long statements, long bytesSent, long nanos, long allocatedBytes) {
	}

	record Memory(long peakHeapBytes, long peakEntities) {
	}

	// 묶음 하나를 읽은 결과. held는 메모리를 재는 동안 읽은 목록을 살려 두려고 들고 있는다
	record ChunkRead(Map<Long, Long> amounts, Object held) {
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private EntityManagerFactory entityManagerFactory;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;

	private final List<String> report = new ArrayList<>();
	private final List<String> csv = new ArrayList<>();
	private final List<String> explainRows = new ArrayList<>();
	private final List<String> explainAnalyze = new ArrayList<>();

	@Test
	void compare() throws IOException {
		csv.add("table,targets,charges,method,chunk,statements,kb_sent,ms_median,ms_min,ms_max,alloc_mb,"
			+ "heap_mib_median,heap_mib_min,heap_mib_max,entities");
		for (Map.Entry<Integer, List<Integer>> table : TARGETS_BY_TABLE.entrySet()) {
			int tableSize = table.getKey();
			List<Long> shuffled = LongStream.rangeClosed(1, tableSize).boxed().collect(Collectors.toList());
			Collections.shuffle(shuffled, new Random(SEED));
			int maxTargets = Collections.max(table.getValue());
			Set<Long> targetSet = new HashSet<>(shuffled.subList(0, maxTargets));
			for (int charges : CHARGES_PER_MEMBER) {
				seed(tableSize, charges, targetSet);
				jdbcTemplate.queryForList("analyze table cash_wallet, cash_wallet_charge");
				for (int targetCount : table.getValue()) {
					List<Long> targets = shuffled.subList(0, targetCount).stream().sorted().toList();
					measure(tableSize, targetCount, charges, targets);
				}
			}
		}
		write();
	}

	private void measure(int tableSize, int targetCount, int charges, List<Long> targets) {
		// 재기 전에 여섯 조합이 모든 대상에게 같은 금액을 내는지 본다
		Map<Long, Long> expected = amounts(COMBOS.getFirst(), targets);
		assertThat(expected).hasSize(targetCount);
		for (Combo combo : COMBOS) {
			assertThat(amounts(combo, targets)).as(combo.label()).isEqualTo(expected);
		}

		Map<Combo, List<Timing>> timings = new HashMap<>();
		for (int round = 0; round < WARMUP + ROUNDS; round++) {
			// 순서가 결과를 기울이지 않게 회차마다 첫 조합을 한 칸씩 민다
			for (int i = 0; i < COMBOS.size(); i++) {
				Combo combo = COMBOS.get((i + round) % COMBOS.size());
				Timing timing = time(combo, targets);
				if (round >= WARMUP) {
					timings.computeIfAbsent(combo, key -> new ArrayList<>()).add(timing);
				}
			}
		}
		Map<Combo, List<Memory>> memories = new HashMap<>();
		for (int round = 0; round < MEMORY_ROUNDS; round++) {
			for (int i = 0; i < COMBOS.size(); i++) {
				Combo combo = COMBOS.get((i + round) % COMBOS.size());
				memories.computeIfAbsent(combo, key -> new ArrayList<>()).add(memory(combo, targets));
			}
		}

		String condition = String.format(Locale.ROOT, "회원 %,d명 · 대상 %,d명 · 회원당 충전 %d건",
			tableSize, targetCount, charges);
		report.add("### " + condition);
		report.add("");
		report.add("| 조합 | SQL 수 | MySQL이 보낸 KB | 걸린 ms 중앙값 (최소~최대) | 만든 객체 MB "
			+ "| 묶음 하나가 늘린 힙 MiB 중앙값 (최소~최대) | 묶음 하나의 엔티티 수 |");
		report.add("|---|---|---|---|---|---|---|");
		for (Combo combo : COMBOS) {
			List<Timing> runs = timings.get(combo);
			List<Long> nanos = sorted(runs.stream().map(Timing::nanos).toList());
			List<Long> heap = sorted(memories.get(combo).stream().map(Memory::peakHeapBytes).toList());
			long statements = median(runs.stream().map(Timing::statements).toList());
			long kb = median(runs.stream().map(Timing::bytesSent).toList()) / 1024;
			long allocMb = median(runs.stream().map(Timing::allocatedBytes).toList()) / (1024 * 1024);
			long entities = median(memories.get(combo).stream().map(Memory::peakEntities).toList());
			report.add(String.format(Locale.ROOT, "| %s | %d | %,d | %s (%s~%s) | %,d | %s (%s~%s) | %,d |",
				combo.label(), statements, kb, ms(median(nanos)), ms(nanos.getFirst()), ms(nanos.getLast()), allocMb,
				mib(median(heap)), mib(heap.getFirst()), mib(heap.getLast()), entities));
			csv.add(String.join(",", List.of(String.valueOf(tableSize), String.valueOf(targetCount),
				String.valueOf(charges), combo.method().name(), String.valueOf(combo.chunk()),
				String.valueOf(statements), String.valueOf(kb), ms(median(nanos)), ms(nanos.getFirst()),
				ms(nanos.getLast()), String.valueOf(allocMb), mib(median(heap)), mib(heap.getFirst()),
				mib(heap.getLast()), String.valueOf(entities))));
		}
		report.add("");
		explain(condition, targets);
	}

	private Map<Long, Long> amounts(Combo combo, List<Long> targets) {
		return readOnly().execute(status -> read(combo, targets, () -> {
		}, chunk -> {
		}));
	}

	private Timing time(Combo combo, List<Long> targets) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		var threadBean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
		return readOnly().execute(status -> {
			long bytesBefore = bytesSent();
			statistics.clear();
			long allocatedBefore = threadBean.getCurrentThreadAllocatedBytes();
			long start = System.nanoTime();
			read(combo, targets, () -> {
			}, chunk -> {
			});
			long nanos = System.nanoTime() - start;
			long allocated = threadBean.getCurrentThreadAllocatedBytes() - allocatedBefore;
			long statements = statistics.getPrepareStatementCount();
			return new Timing(statements, bytesSent() - bytesBefore, nanos, allocated);
		});
	}

	// 묶음을 읽기 전과, 읽고 비우기 직전에 GC를 불러 묶음 하나가 살려 둔 힙을 잰다. 그중 가장 큰 묶음의 값을 남긴다
	private Memory memory(Combo combo, List<Long> targets) {
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		long[] before = new long[1];
		long[] peak = new long[2];
		readOnly().execute(status -> read(combo, targets,
			() -> {
				memoryBean.gc();
				before[0] = memoryBean.getHeapMemoryUsage().getUsed();
			},
			chunk -> {
				long entities = entityManager.unwrap(Session.class).getStatistics().getEntityCount();
				memoryBean.gc();
				long held = memoryBean.getHeapMemoryUsage().getUsed() - before[0];
				Reference.reachabilityFence(chunk);
				peak[0] = Math.max(peak[0], held);
				peak[1] = Math.max(peak[1], entities);
			}));
		return new Memory(peak[0], peak[1]);
	}

	private Map<Long, Long> read(Combo combo, List<Long> targets, Runnable beforeChunk,
		Consumer<ChunkRead> afterChunk) {
		Map<Long, Long> amounts = new HashMap<>();
		for (int from = 0; from < targets.size(); from += combo.chunk()) {
			List<Long> ids = targets.subList(from, Math.min(from + combo.chunk(), targets.size()));
			beforeChunk.run();
			ChunkRead chunk = combo.method() == Method.IN_TWICE ? readInTwice(ids) : readFetchJoin(ids);
			afterChunk.accept(chunk);
			amounts.putAll(chunk.amounts());
			entityManager.clear();
		}
		return amounts;
	}

	// CashCardCancelDeadlineNoticeUseCase.notifyChunk와 같은 조회
	private ChunkRead readInTwice(List<Long> ids) {
		Map<Long, Wallet> wallets = walletRepository.findAllById(ids).stream()
			.collect(Collectors.toMap(Wallet::getId, Function.identity()));
		Map<Long, List<WalletCharge>> charges = walletChargeRepository
			.findByWalletIdInAndStatus(ids, ChargeStatus.COMPLETED).stream()
			.collect(Collectors.groupingBy(charge -> charge.getWallet().getId()));
		Map<Long, Long> amounts = new HashMap<>();
		for (long id : ids) {
			amounts.put(id, remaining(wallets.get(id).getBalance(), charges.getOrDefault(id, List.of())));
		}
		return new ChunkRead(amounts, List.of(wallets, charges));
	}

	private ChunkRead readFetchJoin(List<Long> ids) {
		Map<Long, List<WalletCharge>> charges = entityManager.createQuery(FETCH_JOIN, WalletCharge.class)
			.setParameter("ids", ids)
			.setParameter("status", ChargeStatus.COMPLETED)
			.getResultList().stream()
			.collect(Collectors.groupingBy(charge -> charge.getWallet().getId()));
		Map<Long, Long> amounts = new HashMap<>();
		charges.forEach((id, list) -> amounts.put(id, remaining(list.getFirst().getWallet().getBalance(), list)));
		return new ChunkRead(amounts, charges);
	}

	private static long remaining(long balance, List<WalletCharge> charges) {
		return CashRefundableChargeQueryUseCase.refundable(balance, charges, NOW).charges().stream()
			.mapToLong(RefundableChargeDto::remainingAmount)
			.sum();
	}

	private TransactionTemplate readOnly() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(true);
		return template;
	}

	// 같은 트랜잭션 = 같은 연결이라, MySQL이 이 연결로 보낸 바이트의 차이가 받은 데이터 양이다
	private long bytesSent() {
		Object[] row = (Object[])entityManager.createNativeQuery("show session status like 'Bytes_sent'")
			.getSingleResult();
		return Long.parseLong(row[1].toString());
	}

	// 대상 회원은 충전 1건이 결제취소 마지막 날이 정확히 3일 뒤다. 나머지 충전과 대상이 아닌 회원의 충전은 사흘씩 떨어뜨린다
	private void seed(int tableSize, int chargesPerMember, Set<Long> targets) {
		jdbcTemplate.execute((ConnectionCallback<Void>)connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("set foreign_key_checks = 0");
				statement.execute("truncate table cash_wallet_charge");
				statement.execute("truncate table cash_wallet");
				statement.execute("truncate table cash_member");
				statement.execute("set foreign_key_checks = 1");
			}
			return null;
		});
		String created = "'2026-01-01 00:00:00'";
		long balance = chargesPerMember * 10_000L - 3_000;
		List<String> members = new ArrayList<>();
		List<String> wallets = new ArrayList<>();
		List<String> charges = new ArrayList<>();
		for (long id = 1; id <= tableSize; id++) {
			members.add("(" + id + "," + created + "," + created + ",'러너" + id + "')");
			wallets.add("(" + id + "," + id + "," + balance + ",0," + created + "," + created + ")");
			boolean target = targets.contains(id);
			for (int k = 0; k < chargesPerMember; k++) {
				LocalDateTime approvedAt = DEADLINE_APPROVED.minusDays(3L * k + (target ? 0 : 1));
				String approved = "'" + approvedAt.toString().replace('T', ' ') + "'";
				charges.add("(" + id + ",10000,0,'TOSS','m" + id + "_" + k + "','COMPLETED',"
					+ approved + "," + approved + ",0," + created + "," + created + ")");
			}
			// 충전은 지갑을 가리키는 외래 키가 있어, 충전을 넣기 전에 그 회원·지갑을 먼저 넣는다
			if (members.size() >= INSERT_ROWS || charges.size() >= INSERT_ROWS) {
				flushMembers(members, wallets);
				flushCharges(charges);
			}
		}
		flushMembers(members, wallets);
		flushCharges(charges);
	}

	private void flushMembers(List<String> members, List<String> wallets) {
		if (members.isEmpty()) {
			return;
		}
		jdbcTemplate.update("insert into cash_member (id, create_date, modify_date, nickname) values "
			+ String.join(",", members));
		jdbcTemplate.update("insert into cash_wallet (id, holder_id, balance, version, create_date, modify_date) "
			+ "values " + String.join(",", wallets));
		members.clear();
		wallets.clear();
	}

	private void flushCharges(List<String> charges) {
		if (charges.isEmpty()) {
			return;
		}
		jdbcTemplate.update("insert into cash_wallet_charge (wallet_id, amount, canceled_amount, pg_provider, "
			+ "idempotency_key, status, charge_date, approved_at, review_flagged, create_date, modify_date) values "
			+ String.join(",", charges));
		charges.clear();
	}

	// 조합마다 첫 묶음의 IN 목록으로 실행 계획을 본다. 묶음이 대상보다 크면 대상 전체가 목록이다
	private void explain(String condition, List<Long> targets) {
		for (Combo combo : COMBOS) {
			String ids = targets.subList(0, Math.min(combo.chunk(), targets.size())).stream()
				.map(String::valueOf).collect(Collectors.joining(","));
			Map<String, String> queries = new LinkedHashMap<>();
			if (combo.method() == Method.IN_TWICE) {
				queries.put("지갑 IN", "select * from cash_wallet where id in (" + ids + ")");
				queries.put("충전 IN", "select * from cash_wallet_charge where wallet_id in (" + ids + ")"
					+ " and status = 'COMPLETED'");
			} else {
				queries.put("fetch join", "select * from cash_wallet_charge c join cash_wallet w on w.id = c.wallet_id"
					+ " where c.wallet_id in (" + ids + ") and c.status = 'COMPLETED'");
			}
			queries.forEach((name, sql) -> {
				jdbcTemplate.queryForList("explain " + sql).forEach(row -> explainRows.add(String.join(" | ",
					"| " + condition, combo.label(), name, String.valueOf(row.get("table")),
					String.valueOf(row.get("type")), String.valueOf(row.get("key")), row.get("rows") + " |")));
				explainAnalyze.add("## " + condition + " · " + combo.label() + " · " + name);
				explainAnalyze.add(jdbcTemplate.queryForObject("explain analyze " + sql, String.class));
				explainAnalyze.add("");
			});
		}
	}

	private void write() throws IOException {
		List<String> out = new ArrayList<>();
		out.add("# 기한 알림 읽기: 방법 × 묶음 크기");
		out.add("");
		out.add("시간은 예열 " + WARMUP + "회 뒤 " + ROUNDS + "회 중앙값, 메모리는 따로 " + MEMORY_ROUNDS + "회 중앙값");
		out.add("");
		out.addAll(report);
		out.add("## EXPLAIN (조합마다 첫 묶음의 IN 목록)");
		out.add("");
		out.add("| 조건 | 조합 | 쿼리 | 표 | type | key | rows |");
		out.add("|---|---|---|---|---|---|---|");
		out.addAll(explainRows);
		Path dir = Path.of("build", "measure");
		Files.createDirectories(dir);
		Files.write(dir.resolve("notice-read-combos.md"), out);
		Files.write(dir.resolve("notice-read-combos.csv"), csv);
		Files.write(dir.resolve("notice-read-explain-analyze.txt"), explainAnalyze);
	}

	private static List<Long> sorted(List<Long> values) {
		return values.stream().sorted().toList();
	}

	private static long median(List<Long> values) {
		return sorted(values).get(values.size() / 2);
	}

	private static String ms(long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
	}

	private static String mib(long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
	}
}
