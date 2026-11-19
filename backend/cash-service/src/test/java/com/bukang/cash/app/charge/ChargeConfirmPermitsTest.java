package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.bukang.cash.domain.exception.charge.ChargeBusyException;

class ChargeConfirmPermitsTest {

	@Test
	@DisplayName("동시 개수가 다 차면 일을 하지 않고 429로 바로 거절한다 (토스를 부르기 전이라 돈이 빠지지 않음)")
	void rejectsWithoutRunningWhenFull() throws Exception {
		ChargeConfirmPermits permits = new ChargeConfirmPermits(1);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<String> first = executor.submit(() -> permits.run(() -> {
				holding.countDown();
				await(release);
				return "first";
			}));
			assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

			AtomicBoolean ran = new AtomicBoolean();
			assertThatThrownBy(() -> permits.run(() -> {
				ran.set(true);
				return "second";
			}))
				.isInstanceOf(ChargeBusyException.class)
				.satisfies(e -> assertThat(((ChargeBusyException)e).getStatus())
					.isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
			assertThat(ran).isFalse();

			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("일이 예외로 끝나도 자리를 돌려줘, 다음 요청이 들어올 수 있다")
	void releasesPermitWhenWorkThrows() {
		ChargeConfirmPermits permits = new ChargeConfirmPermits(1);

		assertThatThrownBy(() -> permits.run(() -> {
			throw new IllegalStateException("토스 오류");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(permits.run(() -> "next")).isEqualTo("next");
	}

	@Test
	@DisplayName("동시 개수가 1보다 작으면 서버가 뜨지 않는다")
	void rejectsInvalidLimit() {
		assertThatThrownBy(() -> new ChargeConfirmPermits(0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
