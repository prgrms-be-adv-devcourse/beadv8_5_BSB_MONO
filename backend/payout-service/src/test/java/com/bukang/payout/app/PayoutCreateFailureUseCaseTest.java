package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.PayoutCreateFailureRepository;

/**
 * 기록 전용 유스케이스가 새 트랜잭션으로 저장하고, 저장이 실패해도 예외를 밖으로 던지지 않는지 본다 (BUK-60)
 * 테스트 트랜잭션을 끄고 진짜 프록시(빈)를 써야 트랜잭션 경계가 보인다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(PayoutCreateFailureUseCase.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class PayoutCreateFailureUseCaseTest {
	private static final LocalDate RUN_DATE = LocalDate.of(2026, 11, 10);

	@Autowired
	private PayoutCreateFailureUseCase payoutCreateFailureUseCase;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	// 바깥 트랜잭션을 흉내 낼 때만 쓴다 (운영 코드는 빈을 나눠 @Transactional 프록시를 쓴다, O2 결정)
	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void cleanUp() {
		payoutCreateFailureRepository.deleteAllInBatch();
	}

	private static PayoutCreateFailure mismatch() {
		return PayoutCreateFailure.sellerRace(PayoutStage.최종_정산, 1, 10, LocalDate.of(2026, 11, 5),
			PayoutCreateFailureType.주문_정산_대회일_불일치, null, RUN_DATE);
	}

	@Test
	@DisplayName("실패 한 줄을 기록표에 저장한다")
	void recordsFailure() {
		payoutCreateFailureUseCase.record(mismatch());

		assertThat(payoutCreateFailureRepository.findAll())
			.extracting(PayoutCreateFailure::getFailureType)
			.containsExactly(PayoutCreateFailureType.주문_정산_대회일_불일치);
	}

	@Test
	@DisplayName("바깥 트랜잭션이 롤백돼도 기록은 남는다 (새 트랜잭션, 마무리 7번)")
	void recordSurvivesOuterRollback() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			payoutCreateFailureUseCase.record(mismatch());
			status.setRollbackOnly();
		});

		assertThat(payoutCreateFailureRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("기록 저장이 실패해도 예외를 밖으로 던지지 않고 ERROR 로그만 남기며, 바깥 트랜잭션도 커밋된다 (AC-60-5)")
	void recordFailureIsLoggedNotThrown(CapturedOutput output) {
		// 정산 단계가 비어 있어 NOT NULL 칸 저장이 DB에서 실패한다
		PayoutCreateFailure broken = PayoutCreateFailure.raceLookup(null, RUN_DATE.minusDays(7), RUN_DATE.minusDays(1),
			RUN_DATE);

		assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			payoutCreateFailureUseCase.record(broken))).doesNotThrowAnyException();

		assertThat(payoutCreateFailureRepository.count()).isZero();
		assertThat(output.getAll().lines().filter(line -> line.contains("정산 생성 실패를 기록표에 남기지 못했습니다")))
			.hasSize(1)
			.allMatch(line -> line.contains("ERROR"));
	}
}
