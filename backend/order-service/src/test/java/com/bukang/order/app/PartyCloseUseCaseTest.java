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

import com.bukang.order.domain.Gender;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
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
	private static final long RACE = 5;
	private static final long OTHER_RACE = 6;
	private static final ParticipantInfo PARTICIPANT = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25),
		Gender.MALE, "010-1234-5678", "김가족", "010-9876-5432", "L",
		new ShippingAddress("김러너", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));

	@Autowired
	private PartyCloseUseCase partyCloseUseCase;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private PartyPaymentDeadlineUseCase partyPaymentDeadlineUseCase;

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
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
	@DisplayName("대회가 취소되면 그 대회의 끝나지 않은 파티를 종료하고 미결제 신청서를 끝낸다. 두 번 불러도 한 번만 바꾼다")
	void closePartiesOfCanceledRace() {
		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		long recruiting = party(RACED_COURSE, PartyStatus.RECRUITING);
		long recruitingMember = participation(recruiting, 11, null);
		long paying = party(RACED_COURSE, PartyStatus.PAYING);
		long unpaid = participation(paying, 12, ParticipationStatus.APPLIED);
		long ordered = participation(paying, 13, ParticipationStatus.APPLIED);
		orderFor(ordered, now);
		long paidInPaying = participation(paying, 14, ParticipationStatus.CONFIRMED);
		long paid = party(RACED_COURSE, PartyStatus.PAID);
		long otherRace = party(RACED_COURSE, PartyStatus.RECRUITING, OTHER_RACE);

		assertThat(partyCloseUseCase.closeCanceledRace(RACE, now)).isEqualTo(3);
		assertThat(partyCloseUseCase.closeCanceledRace(RACE, now)).isZero();

		assertThat(statusOf(recruiting)).isEqualTo(PartyStatus.CLOSED);
		assertThat(statusOf(paying)).isEqualTo(PartyStatus.CLOSED);
		assertThat(statusOf(paid)).isEqualTo(PartyStatus.CLOSED);
		assertThat(statusOf(otherRace)).isEqualTo(PartyStatus.RECRUITING);
		assertThat(participationStatusOf(recruitingMember)).isEqualTo(ParticipationStatus.CANCELED);
		assertThat(participationStatusOf(unpaid)).isEqualTo(ParticipationStatus.RELEASED);
		// 주문을 만든 신청서는 주문 만료가, 결제한 신청서는 대회 취소 환불이 끝낸다
		assertThat(participationStatusOf(ordered)).isEqualTo(ParticipationStatus.APPLIED);
		assertThat(participationStatusOf(paidInPaying)).isEqualTo(ParticipationStatus.CONFIRMED);
		// 결제 기한이 지나도 결제 마감 배치는 종료된 파티를 다시 건드리지 않는다
		assertThat(partyPaymentDeadlineUseCase.finishDue(now.plusHours(2))).isZero();
		assertThat(statusOf(paying)).isEqualTo(PartyStatus.CLOSED);
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

		PartyCloseUseCase useCase = new PartyCloseUseCase(repository, mock(ParticipationRepository.class),
			mock(OrderItemRepository.class), port, mock(TransactionTemplate.class));
		useCase.closeFinishedRaces(LocalDateTime.now());

		verify(repository).findIdsByRaceCourseIdAndStatus(RACED_COURSE, PartyStatus.PAID);
	}

	private PartyStatus statusOf(long partyId) {
		return partyRepository.findById(partyId).orElseThrow().getStatus();
	}

	private ParticipationStatus participationStatusOf(long participationId) {
		return participationRepository.findById(participationId).orElseThrow().getStatus();
	}

	private long party(long raceCourseId, PartyStatus status) {
		return party(raceCourseId, status, RACE);
	}

	private long party(long raceCourseId, PartyStatus status, long raceId) {
		Party party = Party.create(CREW, LEADER, raceId, raceCourseId, LocalDateTime.now());
		ReflectionTestUtils.setField(party, "status", status);
		if (status == PartyStatus.PAYING) {
			ReflectionTestUtils.setField(party, "paymentDueAt", LocalDateTime.now().plusHours(1));
		}
		return partyRepository.save(party).getId();
	}

	// status가 null이면 확보 전(PENDING)으로 둔다
	private long participation(long partyId, long memberId, ParticipationStatus status) {
		Participation participation = Participation.partyMember(memberId, RACE, RACED_COURSE, partyId, PARTICIPANT,
			LocalDateTime.now());
		if (status != null) {
			participation.markApplied(LocalDateTime.now().plusHours(1));
		}
		if (status == ParticipationStatus.CONFIRMED) {
			participation.markConfirmed(LocalDateTime.now());
		}
		return participationRepository.save(participation).getId();
	}

	private void orderFor(long participationId, LocalDateTime now) {
		Participation participation = participationRepository.findById(participationId).orElseThrow();
		orderRepository.save(Order.forParticipation(participation, courseOn(RACED_COURSE, now.plusDays(30)),
			"key-" + participationId, now.plusHours(1)));
	}

	private static RaceCourseInfo courseOn(long raceCourseId, LocalDateTime raceDate) {
		RaceCourseInfo open = StubRaceCoursePort.openCourse(raceCourseId, 5, 14, null, LocalDate.now().minusDays(10));
		return new RaceCourseInfo(open.raceCourseId(), open.raceId(), raceDate, open.applicationOpenAt(),
			open.applicationCloseAt(), open.minAge(), open.maxAge(), open.sellerId(), open.courseName(), open.price(),
			open.kitIncluded(), open.refundDeadline(), open.scheduleChangedAt(), open.canceled(),
			open.tshirtSizeOption());
	}
}
