package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyMemberRepository.JoinedParty;

class CrewMemberLeftUseCaseFailureTest {
	private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

	private final PartyMemberRepository partyMemberRepository = mock();
	private final PartyUseCase partyUseCase = mock();
	private final CrewMemberLeftUseCase useCase = new CrewMemberLeftUseCase(partyMemberRepository, partyUseCase,
		new TransactionTemplate(mock(PlatformTransactionManager.class)));

	@Test
	@DisplayName("한 파티에서 실패해도 다음 파티는 처리하고, 끝에 예외를 던져 메시지를 다시 처리하게 한다")
	void continuesAfterFailure() {
		given(partyMemberRepository.findJoinedParties(eq(7L), eq(2L), anyCollection()))
			.willReturn(List.of(new JoinedParty(1, PartyStatus.RECRUITING),
				new JoinedParty(2, PartyStatus.RECRUITING)));
		given(partyUseCase.removeCrewLeaver(1, 2, JOINED_AT, NOW)).willThrow(new IllegalStateException("신청서 없음"));

		assertThatThrownBy(() -> useCase.removeFromParties(7, 2, JOINED_AT, NOW))
			.isInstanceOf(IllegalStateException.class);
		verify(partyUseCase).removeCrewLeaver(2, 2, JOINED_AT, NOW);
	}
}
