package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.exception.ParticipationNotCancelableException;
import com.bukang.order.domain.exception.ParticipationNotEditableException;
import com.bukang.order.domain.exception.ParticipationNotPayableException;

/**
 * 참가 신청 상태 전이: 확보 전 → 결제 대기 → 참가 확정 → 취소, 미결제 해제·확보 실패 (CRU-149)
 * 끝난 상태가 되면 active_flag가 비어 같은 대회에 다시 신청할 수 있다(한 사람 한 대회 1건 유니크에서 빠짐)
 * [결제하기] 전 취소(PENDING)는 이벤트를 발행해 스프링이 필요하므로 거절하는 경우만 본다
 */
class ParticipationStateTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final LocalDateTime DUE = NOW.plusMinutes(15);

	@Test
	@DisplayName("자리를 확보하면 결제 대기, 결제하면 참가 확정, 취소하면 끝난 신청이 되어 active_flag가 빈다")
	void applyConfirmCancel() {
		Participation participation = newParticipation();
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.PENDING);
		assertThat(participation.getActiveFlag()).isTrue();

		participation.markApplied(DUE);
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.APPLIED);
		assertThat(participation.getPaymentDueAt()).isEqualTo(DUE);

		participation.markConfirmed(NOW.plusMinutes(1));
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
		assertThat(participation.getActiveFlag()).isTrue();

		participation.markCanceled(NOW.plusDays(1));
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CANCELED);
		assertThat(participation.getActiveFlag()).isNull();
		assertThat(participation.getCanceledAt()).isEqualTo(NOW.plusDays(1));
	}

	@Test
	@DisplayName("결제 대기가 아니면 참가를 확정할 수 없고, 참가 확정이 아니면 취소할 수 없다")
	void rejectOutOfOrder() {
		Participation participation = newParticipation();

		assertThatThrownBy(() -> participation.markConfirmed(NOW)).isInstanceOf(ParticipationNotPayableException.class);
		assertThatThrownBy(() -> participation.markCanceled(NOW)).isInstanceOf(ParticipationNotPayableException.class);

		participation.markApplied(DUE);
		assertThatThrownBy(() -> participation.markApplied(DUE)).isInstanceOf(ParticipationNotPayableException.class);
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.APPLIED);
	}

	@Test
	@DisplayName("미결제 해제는 결제 대기일 때만 바꾸고 바꿨는지 돌려준다")
	void markReleased() {
		Participation pending = newParticipation();
		assertThat(pending.markReleased(DUE)).isFalse();
		assertThat(pending.getStatus()).isEqualTo(ParticipationStatus.PENDING);

		Participation applied = newParticipation();
		applied.markApplied(DUE);
		assertThat(applied.markReleased(DUE)).isTrue();
		assertThat(applied.getStatus()).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(applied.getActiveFlag()).isNull();
		assertThat(applied.getReleasedAt()).isEqualTo(DUE);
	}

	@Test
	@DisplayName("자리를 잡으러 간 뒤 기한이 지나면 미결제 해제로 끝내고, 기한 전이거나 표시가 없으면 그대로 둔다")
	void releaseAbandonedHold() {
		Participation notRequested = newParticipation();
		assertThat(notRequested.releaseAbandonedHold(DUE)).isFalse();

		Participation requested = newParticipation();
		requested.markHoldRequested(DUE);
		assertThat(requested.visiblePaymentDueAt()).isNull();
		assertThat(requested.releaseAbandonedHold(DUE.minusNanos(1))).isFalse();
		assertThat(requested.getStatus()).isEqualTo(ParticipationStatus.PENDING);

		assertThat(requested.releaseAbandonedHold(DUE)).isTrue();
		assertThat(requested.getStatus()).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(requested.getActiveFlag()).isNull();
	}

	@Test
	@DisplayName("확보에 실패하면 끝난 신청이 되고, 끝난 신청은 참가자 정보를 고칠 수 없다")
	void holdFailedThenNotEditable() {
		Participation participation = newParticipation();

		participation.markHoldFailed();

		assertThat(participation.isHoldFailed()).isTrue();
		assertThat(participation.getActiveFlag()).isNull();
		assertThatThrownBy(() -> participation.changeParticipant(participant("박러너")))
			.isInstanceOf(ParticipationNotEditableException.class);
	}

	@Test
	@DisplayName("결제 대기·참가 확정·끝난 신청은 [결제하기] 전 취소로 무를 수 없다")
	void cancelBeforePaymentRejected() {
		Participation applied = newParticipation();
		applied.markApplied(DUE);
		assertThatThrownBy(() -> applied.cancelBeforePayment(NOW))
			.isInstanceOf(ParticipationNotCancelableException.class);

		applied.markConfirmed(NOW);
		assertThatThrownBy(() -> applied.cancelBeforePayment(NOW))
			.isInstanceOf(ParticipationNotCancelableException.class);

		Participation failed = newParticipation();
		failed.markHoldFailed();
		assertThatThrownBy(() -> failed.cancelBeforePayment(NOW))
			.isInstanceOf(ParticipationNotCancelableException.class);
		assertThat(failed.getStatus()).isEqualTo(ParticipationStatus.FAILED);
	}

	private static Participation newParticipation() {
		return Participation.individual(1, 100, 10, participant("김러너"), NOW);
	}

	private static ParticipantInfo participant(String name) {
		return new ParticipantInfo(name, LocalDate.of(1994, 6, 25), Gender.MALE, "010-1234-5678",
			"김가족", "010-9876-5432", "L",
			new ShippingAddress(name, "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));
	}
}
