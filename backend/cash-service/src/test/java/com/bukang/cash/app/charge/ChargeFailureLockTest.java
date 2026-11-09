package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.cash.RedisTestcontainersConfiguration;
import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.exception.charge.ChargeCooldownException;
import com.bukang.cash.domain.exception.charge.PaymentApprovalFailedException;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.dto.charge.CashChargeResponseDto;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 실패 잠금은 Redis의 고정 구간 카운터로 세므로, 가짜 Redis로는 확인할 수 없어 진짜 Redis 컨테이너로 돈다
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestcontainersConfiguration.class)
class ChargeFailureLockTest {
	private static final long CUSTOMER_ID = 7;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletChargeRepository walletChargeRepository;
	@Autowired
	private WalletLedgerRepository walletLedgerRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private PaymentClient paymentClient;

	@AfterEach
	void tearDown() {
		walletChargeRepository.deleteAll();
		walletLedgerRepository.deleteAll();
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
		redisTemplate.execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	@Test
	@DisplayName("1시간 안에 결제 실패를 6번째 겪으면, 그다음 충전 시도부터 1시간 잠금으로 거절한다")
	void locksForOneHourAfterSixthFailureWithinOneHour() {
		createCustomerWallet();
		willThrow(new PaymentApprovalFailedException("카드사에서 승인을 거절했습니다.", "REJECT_CARD_COMPANY", false))
			.given(paymentClient).approve(anyString(), anyString(), anyLong());

		for (int i = 1; i <= 6; i++) {
			cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_" + i, "order_" + i, 10_000);
			if (i == 3) {
				// 5분/3회 쿨다운은 이 테스트의 관심사가 아니다. 실제로는 시간이 지나 풀리지만,
				// 여기서는 그 독립된 키만 지워 "1시간 안에 실패 6번"을 빠르게 재현한다
				redisTemplate.delete("charge:" + CUSTOMER_ID + ":attempts");
				redisTemplate.delete("charge:" + CUSTOMER_ID + ":cooldown");
			}
		}

		// given(mock.method())으로 재스터빙하면 이미 걸려 있는 예외 스텁이 그 자리에서 바로 실행돼
		// 재스터빙 자체가 실패하므로, willReturn(...).given(...) 순서로 바꾼다
		willReturn(new PaymentApprovalResult(10_000, "toss_key", null))
			.given(paymentClient).approve(anyString(), anyString(), anyLong());
		// 5분/3회 쿨다운과 무관하다는 걸 분명히 하려고, 7번째 호출 전에도 그 독립된 키를 지운다
		redisTemplate.delete("charge:" + CUSTOMER_ID + ":attempts");
		redisTemplate.delete("charge:" + CUSTOMER_ID + ":cooldown");

		assertThatThrownBy(() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_7", "order_7", 10_000))
			.isInstanceOf(ChargeCooldownException.class);

		verify(paymentClient, times(6)).approve(anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("1시간 안에 실패하다가 중간에 한 번 성공하면 실패 카운트가 리셋돼, 그 뒤 5번만 더 실패해서는 잠기지 않는다")
	void resetsFailureCountAfterASuccessWithinTheWindow() {
		createCustomerWallet();
		willThrow(new PaymentApprovalFailedException("카드사에서 승인을 거절했습니다.", "REJECT_CARD_COMPANY", false))
			.given(paymentClient).approve(anyString(), anyString(), anyLong());

		for (int i = 1; i <= 3; i++) {
			confirm("pay_" + i, "order_" + i);
		}

		// 리셋 대상인 성공 1건. 특정 orderId에만 적용해, 나머지 실패 스텁은 그대로 둔다
		willReturn(new PaymentApprovalResult(10_000, "toss_key_success", null))
			.given(paymentClient).approve(anyString(), eq("order_success"), anyLong());
		CashChargeResponseDto success = confirm("pay_success", "order_success");
		assertThat(success.outcome()).isEqualTo(CashChargeResponseDto.Outcome.COMPLETED);

		// 리셋됐다면 이 5번은 누적 5번뿐이라 한도(5) 안쪽이라 잠기지 않아야 한다
		for (int i = 4; i <= 8; i++) {
			confirm("pay_" + i, "order_" + i);
		}

		willReturn(new PaymentApprovalResult(10_000, "toss_key_last", null))
			.given(paymentClient).approve(anyString(), eq("order_9"), anyLong());
		CashChargeResponseDto response = confirm("pay_9", "order_9");

		assertThat(response.outcome()).isEqualTo(CashChargeResponseDto.Outcome.COMPLETED);
	}

	// 5분/3회 쿨다운은 이 테스트들의 관심사가 아니므로, 매번 그 독립된 키를 지운 뒤 호출한다
	private CashChargeResponseDto confirm(String paymentKey, String orderId) {
		redisTemplate.delete("charge:" + CUSTOMER_ID + ":attempts");
		redisTemplate.delete("charge:" + CUSTOMER_ID + ":cooldown");
		return cashFacade.confirmWalletCharge(CUSTOMER_ID, paymentKey, orderId, 10_000);
	}

	private Wallet createCustomerWallet() {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			return walletRepository.save(new Wallet(member));
		});
	}
}
