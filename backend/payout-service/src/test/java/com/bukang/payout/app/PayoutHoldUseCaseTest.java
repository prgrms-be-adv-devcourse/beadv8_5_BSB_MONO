package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutHoldNotFoundException;
import com.bukang.payout.domain.exception.PayoutNotFoundException;
import com.bukang.payout.dto.PayoutHoldDto;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutHoldUseCaseTest {
	private static final int ADMIN_ID = 99;
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutHoldUseCase useCase() {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar());
	}

	private PayoutPayUseCase payUseCase() {
		return new PayoutPayUseCase(payoutRepository);
	}

	// 2026-09분: 지급 예정일 10/20
	private Payout saveSeptemberPayout() {
		return payoutRepository.save(Payout.finalStage(1, 10, "테스트 대회", YearMonth.of(2026, 9), 0L,
			LocalDateTime.of(2026, 9, 29, 3, 0), new BusinessDayCalendar()));
	}

	private PayoutHoldDto holdOf(Payout payout) {
		return useCase().hold(payout.getId(), PayoutHoldReason.ACCOUNT_NOT_REGISTERED, "계좌 확인 중", ADMIN_ID, HELD_AT);
	}

	private Payout reload(Payout payout) {
		entityManager.flush();
		entityManager.clear();
		return payoutRepository.findById(payout.getId()).orElseThrow();
	}

	@Test
	@DisplayName("보류를 걸면 정산 내역이 HELD가 되고, 지급보류 ID가 HD-보류한 날-16진수 4자리로 붙는다")
	void hold() {
		Payout payout = saveSeptemberPayout();

		PayoutHoldDto hold = holdOf(payout);

		assertThat(hold.getHoldCode()).isEqualTo("HD-20261012-%04X".formatted(hold.getId() & 0xFFFF));
		assertThat(hold.getPayoutId()).isEqualTo(payout.getId());
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
		assertThat(hold.getHeldBy()).isEqualTo(ADMIN_ID);
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(payoutHoldRepository.findById(hold.getId()).orElseThrow().getHoldCode())
			.isEqualTo(hold.getHoldCode());
	}

	@Test
	@DisplayName("보류 중인 정산 내역은 10/20 배치에서 지급되지 않고, 10/21에 풀면 11/20 배치에서 지급된다")
	void releasedPayoutIsPaidOnNextPayDate() {
		Payout payout = saveSeptemberPayout();
		PayoutHoldDto hold = holdOf(payout);

		payUseCase().run(LocalDate.of(2026, 10, 20), LocalDateTime.of(2026, 10, 20, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.HELD);

		// 10월 지급일(10/20)이 지났으므로 다음 지급일은 11/20이다
		PayoutHoldDto released = useCase().release(hold.getId(), 77, LocalDateTime.of(2026, 10, 21, 9, 0));
		assertThat(released.getStatus()).isEqualTo(PayoutHoldStatus.RELEASED);
		assertThat(released.getReleasedBy()).isEqualTo(77);
		assertThat(reload(payout).getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 11, 20));

		payUseCase().run(LocalDate.of(2026, 11, 19), LocalDateTime.of(2026, 11, 19, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.CALCULATED);

		payUseCase().run(LocalDate.of(2026, 11, 20), LocalDateTime.of(2026, 11, 20, 4, 0));
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.PAID);
	}

	@Test
	@DisplayName("정산 내역을 미리 읽지 않고 보류만 읽어 풀어도(hold.getPayout()이 프록시) 풀린다")
	void releaseWithLazyPayoutProxy() {
		Payout payout = saveSeptemberPayout();
		PayoutHoldDto hold = holdOf(payout);
		// 실제 요청처럼 영속성 컨텍스트를 비워, 보류를 읽을 때 정산 내역이 프록시로 들어오게 한다
		entityManager.flush();
		entityManager.clear();

		PayoutHoldDto released = useCase().release(hold.getId(), ADMIN_ID, LocalDateTime.of(2026, 10, 15, 9, 0));

		assertThat(released.getStatus()).isEqualTo(PayoutHoldStatus.RELEASED);
		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("다른 정산 내역의 보류로는 풀 수 없다 (ID로 비교)")
	void cannotReleaseWithOtherPayoutsHold() {
		Payout payout = saveSeptemberPayout();
		Payout other = payoutRepository.save(Payout.finalStage(2, 10, "테스트 대회", YearMonth.of(2026, 9), 0L,
			LocalDateTime.of(2026, 9, 29, 3, 0), new BusinessDayCalendar()));
		holdOf(payout);
		PayoutHoldDto otherHold = holdOf(other);
		entityManager.flush();
		entityManager.clear();

		Payout loaded = payoutRepository.findById(payout.getId()).orElseThrow();
		PayoutHold otherHoldEntity = payoutHoldRepository.findById(otherHold.getId()).orElseThrow();

		assertThatThrownBy(() -> loaded.release(otherHoldEntity, ADMIN_ID, LocalDateTime.of(2026, 10, 15, 9, 0),
			new BusinessDayCalendar()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(loaded.getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(otherHoldEntity.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
	}

	@Test
	@DisplayName("없는 정산 내역에 보류를 걸거나 없는 보류를 풀면 찾을 수 없다는 예외가 난다")
	void notFound() {
		assertThatThrownBy(() -> useCase().hold(9999, PayoutHoldReason.RACE_CANCELED, null, ADMIN_ID, HELD_AT))
			.isInstanceOf(PayoutNotFoundException.class);
		assertThatThrownBy(() -> useCase().release(9999, ADMIN_ID, HELD_AT))
			.isInstanceOf(PayoutHoldNotFoundException.class);
	}

	@Test
	@DisplayName("지급 배치가 읽은 뒤 보류가 걸리면, 배치가 그 정산 내역을 PAID로 덮어쓰지 못한다 (@Version)")
	void staleBatchCannotOverwriteHold() {
		Payout payout = saveSeptemberPayout();
		entityManager.flush();
		entityManager.clear();

		// 지급 배치가 CALCULATED(version 0)로 읽어 둔 상태를 흉내 낸다
		Payout readByBatch = payoutRepository.findById(payout.getId()).orElseThrow();
		// 상태를 바꾸면 이력 목록(LAZY)에 한 줄을 더하므로, 세션이 있을 때 미리 읽어 둔다 (운영의 지급 배치는 트랜잭션 안이다)
		readByBatch.getStatusHistories().size();
		entityManager.detach(readByBatch);

		// 그 사이 관리자가 보류를 건다 (version 1)
		holdOf(payout);
		entityManager.flush();
		entityManager.clear();

		readByBatch.pay(LocalDateTime.of(2026, 10, 20, 4, 0));
		assertThatThrownBy(() -> payoutRepository.saveAndFlush(readByBatch))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}
}
