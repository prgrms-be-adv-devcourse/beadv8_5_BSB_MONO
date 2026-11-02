package com.bukang.queue.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.support.QueueIntegrationTest;

/**
 * 같은 대회의 옛 메시지와 새 메시지가 동시에 처리돼도 마지막에 새 값이 남는지 확인한다.
 * 재시도 리스너와 본 리스너는 다른 쓰레드라, 재시도로 늦게 온 옛 메시지가 새 메시지와 같은 순간에 처리될 수 있다.
 * 동기화는 대회 행을 잠그고 읽으므로, 나중에 온 쪽은 먼저 잠근 쪽이 커밋할 때까지 기다렸다가 방금 커밋된 수정 일시와 비교한다.
 * - 옛 메시지가 먼저 잠그면: 새 메시지가 기다렸다가 반영한다 (잠그지 않으면 새 메시지가 먼저 커밋하고, 옛 메시지가 나중에 커밋하며 덮는다)
 * - 새 메시지가 먼저 잠그면: 옛 메시지가 기다렸다가 수정 일시 비교로 건너뛴다
 */
class QueueSyncConcurrencyTest extends QueueIntegrationTest {
	private static final long RACE_ID = 1;
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 1, 18, 0);
	// 나중에 온 메시지가 잠긴 행 앞에서 기다리는 동안 커밋하지 않고 둘 시간. H2의 잠금 대기 한도(기본 1초)보다 짧게 둔다
	private static final long WAIT_MILLIS = 300;

	@Autowired
	private QueueFacade queueFacade;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	@DisplayName("syncRace: 재시도로 늦게 온 옛 메시지(10시)가 대회 행을 잠그고 커밋하기 전에 새 메시지(11시, 판매 중)가 동시에 처리되면, "
		+ "새 메시지는 잠금이 풀린 뒤 방금 커밋된 10시와 비교해 반영한다. 마지막에 새 값(판매 중, 11시)이 남는다")
	void applyNewerMessageAfterOlderCommits() throws Exception {
		syncRace(RaceSaleStatus.SCHEDULED, at(9));
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			CountDownLatch rowLocked = new CountDownLatch(1);
			// 본 리스너: 새 메시지 (상태 배치가 판매 중으로 바꿈). 옛 메시지가 대회 행을 잠근 뒤에 출발한다
			Future<QueueRace> newer = pool.submit(() -> {
				rowLocked.await();
				return syncRace(RaceSaleStatus.ON_SALE, at(11));
			});

			// 재시도 리스너: 늦게 온 옛 메시지. 대회 행을 잠그고 반영한 뒤 아직 커밋하지 않은 채로 둔다
			transactionTemplate.executeWithoutResult(status -> {
				syncRace(RaceSaleStatus.SCHEDULED, at(10));
				rowLocked.countDown();
				// 새 메시지가 잠긴 행을 읽으려다 기다리는 동안 커밋하지 않고 둔다
				// 잠그지 않으면 새 메시지가 이 사이에 먼저 커밋하고, 이 트랜잭션이 커밋하면서 옛 값으로 덮는다
				waitFor(newer, WAIT_MILLIS);
			});

			// 커밋으로 잠금이 풀리면 새 메시지가 방금 커밋된 10시를 읽고, 11시가 더 늦으므로 반영한다
			newer.get(5, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		// 두 트랜잭션이 모두 끝난 뒤 남은 대회
		QueueRace race = findRace(RACE_ID);
		System.out.println("최종 대회 수정 일시: " + race.getUpdatedAt());
		assertThat(race).usingRecursiveComparison().isEqualTo(
			new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(11)));
	}

	@Test
	@DisplayName("syncRace: 새 메시지(11시, 판매 중)가 대회 행을 잠그고 커밋하기 전에 재시도로 늦게 온 옛 메시지(10시)가 동시에 처리되면, "
		+ "옛 메시지는 잠금이 풀린 뒤 방금 커밋된 11시와 비교해 건너뛴다. 마지막에 새 값(판매 중, 11시)이 남는다")
	void skipOlderMessageAfterNewerCommits() throws Exception {
		syncRace(RaceSaleStatus.SCHEDULED, at(9));
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			CountDownLatch rowLocked = new CountDownLatch(1);
			// 재시도 리스너: 늦게 온 옛 메시지. 새 메시지가 대회 행을 잠근 뒤에 출발한다
			Future<QueueRace> older = pool.submit(() -> {
				rowLocked.await();
				return syncRace(RaceSaleStatus.SCHEDULED, at(10));
			});

			// 본 리스너: 새 메시지 (상태 배치가 판매 중으로 바꿈). 대회 행을 잠그고 반영한 뒤 아직 커밋하지 않은 채로 둔다
			transactionTemplate.executeWithoutResult(status -> {
				syncRace(RaceSaleStatus.ON_SALE, at(11));
				rowLocked.countDown();
				// 옛 메시지가 잠긴 행을 읽으려다 기다리는 동안 커밋하지 않고 둔다
				waitFor(older, WAIT_MILLIS);
			});

			// 커밋으로 잠금이 풀리면 옛 메시지가 방금 커밋된 11시를 읽고, 10시는 더 이르므로 건너뛴다
			older.get(5, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		// 두 트랜잭션이 모두 끝난 뒤 남은 대회
		QueueRace race = findRace(RACE_ID);
		System.out.println("최종 대회 수정 일시: " + race.getUpdatedAt());
		assertThat(race).usingRecursiveComparison().isEqualTo(
			new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(11)));
	}

	private QueueRace syncRace(RaceSaleStatus saleStatus, LocalDateTime updatedAt) {
		return queueFacade.syncRace(RACE_ID, saleStatus, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, updatedAt);
	}

	// millis 동안 future가 끝나기를 기다린다. 그 안에 끝나지 않아도 넘어간다
	private static void waitFor(Future<?> future, long millis) {
		try {
			future.get(millis, TimeUnit.MILLISECONDS);
		} catch (TimeoutException exception) {
			// 잠긴 행 앞에서 아직 기다리는 중이다
			return;
		} catch (InterruptedException | ExecutionException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
