package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.exception.PartyConflictException;

class PartyTest {

	@Test
	@DisplayName("새 파티는 모집 중이고, 참가 링크 토큰은 URL에 그대로 쓸 수 있는 43자이며 파티마다 다르다")
	void create() {
		Party party = Party.create(7L, 3L, 1L, 10L, LocalDateTime.now());
		Party other = Party.create(7L, 4L, 1L, 10L, LocalDateTime.now());

		assertThat(party.getStatus()).isEqualTo(PartyStatus.RECRUITING);
		assertThat(party.getJoinToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(party.getJoinToken()).isNotEqualTo(other.getJoinToken());
	}

	@Test
	@DisplayName("크루 자리 요청 중 표시: 끝 시각이 없으면 1분 안 재요청은 409, 1분이 지나면 받는다")
	void crewHoldCallLimitWithoutEnd() {
		LocalDateTime requested = LocalDateTime.of(2026, 11, 12, 18, 59, 58);
		Party party = Party.create(7L, 3L, 1L, 10L, requested.minusDays(1));
		party.markCrewHoldRequested(requested);

		LocalDateTime justBefore = requested.plus(Party.CREW_HOLD_CALL_LIMIT).minusNanos(1000);
		assertThatThrownBy(() -> party.markCrewHoldRequested(justBefore)).isInstanceOf(PartyConflictException.class);
		assertThatCode(() -> party.markCrewHoldRequested(requested.plus(Party.CREW_HOLD_CALL_LIMIT)))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("크루 자리 요청 중 표시: 요청이 결과를 모른 채 끝났으면(503) 바로 다시 요청할 수 있다")
	void crewHoldCallEnded() {
		LocalDateTime requested = LocalDateTime.of(2026, 11, 12, 18, 59, 58);
		Party party = Party.create(7L, 3L, 1L, 10L, requested.minusDays(1));
		party.markCrewHoldRequested(requested);
		party.markCrewHoldCallEnded(requested.plusSeconds(2));

		assertThatCode(() -> party.markCrewHoldRequested(requested.plusSeconds(3))).doesNotThrowAnyException();
		// 다시 요청하면 끝 시각이 비어 또 부르는 중이 된다
		assertThatThrownBy(() -> party.markCrewHoldRequested(requested.plusSeconds(4)))
			.isInstanceOf(PartyConflictException.class);
	}
}
