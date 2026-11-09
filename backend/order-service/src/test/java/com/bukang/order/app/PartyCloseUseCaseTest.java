package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(StubRaceCoursePortConfig.class)
class PartyCloseUseCaseTest {
	private static final long CREW = 2;
	private static final long LEADER = 1;
	private static final long RACED_COURSE = 10;
	private static final long TODAY_COURSE = 20;
	private static final long MISSING_COURSE = 30;
	private static final long CANCELED_COURSE = 40;
	private static final long FAILING_COURSE = 50;

	@Autowired
	private PartyCloseUseCase partyCloseUseCase;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@AfterEach
	void tearDown() {
		partyRepository.deleteAll();
		raceCoursePort.clear();
	}

	@Test
	@DisplayName("대회 다음 날 결제 끝 파티만 종료로 바꾸고, 두 번 돌려도 한 번만 바꾼다")
	void closeAfterRaceDay() {
		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		raceCoursePort.put(courseOn(RACED_COURSE, now.minusDays(1)));
		raceCoursePort.put(courseOn(TODAY_COURSE, now.toLocalDate().atStartOfDay()));
		long paid = party(RACED_COURSE, PartyStatus.PAID);
		long paying = party(RACED_COURSE, PartyStatus.PAYING);
		long recruiting = party(RACED_COURSE, PartyStatus.RECRUITING);
		long raceToday = party(TODAY_COURSE, PartyStatus.PAID);
		long unknownCourse = party(MISSING_COURSE, PartyStatus.PAID);

		assertThat(partyCloseUseCase.closeFinishedRaces(now)).isEqualTo(1);
		assertThat(partyCloseUseCase.closeFinishedRaces(now)).isZero();

		Party closed = partyRepository.findById(paid).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(PartyStatus.CLOSED);
		assertThat(closed.getClosedAt()).isEqualTo(now);
		assertThat(statusOf(paying)).isEqualTo(PartyStatus.PAYING);
		assertThat(statusOf(recruiting)).isEqualTo(PartyStatus.RECRUITING);
		// 대회 당일은 아직 끝나지 않았다. 상품에서 찾지 못한 종목은 건너뛰고 다음 실행에서 다시 본다
		assertThat(statusOf(raceToday)).isEqualTo(PartyStatus.PAID);
		assertThat(statusOf(unknownCourse)).isEqualTo(PartyStatus.PAID);
	}

	@Test
	@DisplayName("취소된 대회의 결제 끝 파티는 대회일 전이라도 바로 종료한다")
	void closeCanceledRace() {
		LocalDateTime now = LocalDateTime.now();
		raceCoursePort.put(StubRaceCoursePort.canceled(courseOn(CANCELED_COURSE, now.plusDays(30))));
		long canceled = party(CANCELED_COURSE, PartyStatus.PAID);

		assertThat(partyCloseUseCase.closeFinishedRaces(now)).isEqualTo(1);

		assertThat(statusOf(canceled)).isEqualTo(PartyStatus.CLOSED);
	}

	@Test
	@DisplayName("한 종목의 상품 조회가 실패해도 배치를 멈추지 않고 다음 종목을 본다")
	void continueWhenOneCourseLookupFails() {
		PartyRepository repository = mock(PartyRepository.class);
		RaceCoursePort port = mock(RaceCoursePort.class);
		when(repository.findRaceCourseIdsByStatus(PartyStatus.PAID)).thenReturn(List.of(FAILING_COURSE, RACED_COURSE));
		when(port.findCourse(FAILING_COURSE)).thenThrow(new ExternalServiceUnavailableException("상품이 내려갔습니다."));
		when(port.findCourse(RACED_COURSE)).thenReturn(Optional.of(courseOn(RACED_COURSE,
			LocalDateTime.now().minusDays(1))));

		PartyCloseUseCase useCase = new PartyCloseUseCase(repository, port, mock(TransactionTemplate.class));
		useCase.closeFinishedRaces(LocalDateTime.now());

		verify(repository).findIdsByRaceCourseIdAndStatus(RACED_COURSE, PartyStatus.PAID);
	}

	private PartyStatus statusOf(long partyId) {
		return partyRepository.findById(partyId).orElseThrow().getStatus();
	}

	private long party(long raceCourseId, PartyStatus status) {
		Party party = Party.create(CREW, LEADER, 5, raceCourseId, LocalDateTime.now());
		ReflectionTestUtils.setField(party, "status", status);
		return partyRepository.save(party).getId();
	}

	private static RaceCourseInfo courseOn(long raceCourseId, LocalDateTime raceDate) {
		RaceCourseInfo open = StubRaceCoursePort.openCourse(raceCourseId, 5, 14, null, LocalDate.now().minusDays(10));
		return new RaceCourseInfo(open.raceCourseId(), open.raceId(), raceDate, open.applicationOpenAt(),
			open.applicationCloseAt(), open.minAge(), open.maxAge(), open.sellerId(), open.courseName(), open.price(),
			open.kitIncluded(), open.refundDeadline(), open.scheduleChangedAt(), open.canceled(),
			open.tshirtSizeOption());
	}
}
