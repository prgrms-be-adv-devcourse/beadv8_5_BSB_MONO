package com.bukang.cash.app.charge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
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
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.domain.wallet.Wallet;
import com.bukang.cash.out.charge.WalletChargeRepository;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.pg.PaymentApprovalResult;
import com.bukang.cash.out.pg.PaymentClient;
import com.bukang.cash.out.wallet.WalletLedgerRepository;
import com.bukang.cash.out.wallet.WalletRepository;

// 쿨다운은 Redis의 고정 구간 카운터로 세므로, 가짜 Redis로는 확인할 수 없어 진짜 Redis 컨테이너로 돈다
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestcontainersConfiguration.class)
class ChargeAttemptCooldownTest {
	private static final long CUSTOMER_ID = 5;

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
	@DisplayName("같은 회원이 5분 안에 4번째 충전을 시도하면 쿨다운 예외가 나고 PG는 안 불린다 (1~3번째는 정상 통과)")
	void rejectsFourthAttemptWithinFiveMinutes() {
		createCustomerWallet();
		given(paymentClient.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentApprovalResult(10_000, "toss_key", null));

		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_2", "order_2", 10_000);
		cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_3", "order_3", 10_000);

		assertThatThrownBy(() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_4", "order_4", 10_000))
			.isInstanceOf(ChargeCooldownException.class);

		verify(paymentClient, times(3)).approve(anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("이미 쿨다운이 걸려 있으면 시도 횟수와 상관없이 바로 거절하고, 남은 시간을 분 단위로 안내한다")
	void rejectsImmediatelyWhileCooldownActiveAndTellsRemainingMinutes() {
		createCustomerWallet();
		redisTemplate.opsForValue().set("charge:" + CUSTOMER_ID + ":cooldown", "1", Duration.ofMinutes(7));

		assertThatThrownBy(() -> cashFacade.confirmWalletCharge(CUSTOMER_ID, "pay_1", "order_1", 10_000))
			.isInstanceOf(ChargeCooldownException.class)
			.hasMessageContaining("7분");

		verify(paymentClient, never()).approve(anyString(), anyString(), anyLong());
	}

	private Wallet createCustomerWallet() {
		return transactionTemplate.execute(status -> {
			LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 0);
			CashMember member = cashMemberRepository.save(new CashMember(CUSTOMER_ID, now, now, "러너" + CUSTOMER_ID));
			return walletRepository.save(new Wallet(member));
		});
	}
}
