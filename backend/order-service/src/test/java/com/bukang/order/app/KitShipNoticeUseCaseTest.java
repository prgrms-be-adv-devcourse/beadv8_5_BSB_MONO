package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.KitShipNoticeRepository;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.race.RaceSellerPort;
import com.bukang.order.support.StubRaceCoursePort;

class KitShipNoticeUseCaseTest {
	private final KitShipNoticeRepository kitShipNoticeRepository = mock(KitShipNoticeRepository.class);
	private final RaceCoursePort raceCoursePort = mock(RaceCoursePort.class);
	private final RaceSellerPort raceSellerPort = mock(RaceSellerPort.class);
	private final KitShipNoticeUseCase useCase = new KitShipNoticeUseCase(kitShipNoticeRepository, raceCoursePort,
		raceSellerPort, mock(EventPublisher.class), mock(TransactionTemplate.class));

	@Test
	@DisplayName("키트 발송 알림: 판매자 조회(상품 서비스)를 쓸 수 없으면 남은 대회는 부르지 않고 이번 배치를 멈춘다")
	void stopBatchWhenSellerLookupUnavailable() {
		LocalDate yesterday = LocalDate.now().minusDays(1);
		when(kitShipNoticeRepository.findReadyKitCourseIdsNotNoticed()).thenReturn(List.of(1L, 2L));
		when(raceCoursePort.findCourse(1L)).thenReturn(
			Optional.of(StubRaceCoursePort.openCourse(1L, 10L, 14, null, yesterday)));
		when(raceCoursePort.findCourse(2L)).thenReturn(
			Optional.of(StubRaceCoursePort.openCourse(2L, 20L, 14, null, yesterday)));
		when(raceSellerPort.findSellerMemberId(anyLong()))
			.thenThrow(new ExternalServiceUnavailableException("상품 서비스에 연결하지 못했습니다."));

		assertThat(useCase.notifyShippable(LocalDateTime.now())).isZero();

		// 판매자 ID가 아니라 대회 ID로 묻는다 (BUK-237)
		verify(raceSellerPort, times(1)).findSellerMemberId(eq(10L));
	}

	@Test
	@DisplayName("키트 발송 알림: 종목 하나의 상품 조회가 실패해도 배치를 멈추지 않고 다음 대회를 본다")
	void continueWhenOneCourseLookupUnavailable() {
		when(kitShipNoticeRepository.findReadyKitCourseIdsNotNoticed()).thenReturn(List.of(1L, 2L));
		when(raceCoursePort.findCourse(1L))
			.thenThrow(new ExternalServiceUnavailableException("상품 응답 형식이 다릅니다."));
		when(raceCoursePort.findCourse(2L)).thenReturn(Optional.of(
			StubRaceCoursePort.openCourse(2L, 20L, 14, null, LocalDate.now().minusDays(1))));
		when(raceSellerPort.findSellerMemberId(anyLong())).thenReturn(Optional.empty());

		useCase.notifyShippable(LocalDateTime.now());

		verify(raceSellerPort, times(1)).findSellerMemberId(eq(20L));
	}
}
