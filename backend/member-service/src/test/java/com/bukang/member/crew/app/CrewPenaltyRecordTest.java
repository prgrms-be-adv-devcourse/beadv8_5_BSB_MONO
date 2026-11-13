package com.bukang.member.crew.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.bukang.member.crew.domain.exception.CrewPenaltyLookupFailedException;
import com.bukang.member.crew.out.CrewPenaltyRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.product.LowPaymentCrew;
import com.bukang.member.out.product.LowPaymentCrewPort;

/**
 * 결제율 미달 크루의 파티 만들기 제한 저장(CrewPenaltyRecordUseCase)을 확인한다 (BUK-187)
 * - 한국 시간 기준 이번 달과 지난달을 상품 서비스에 묻고, 제한 기간은 저장한 시각부터 30일이다
 * - 이미 저장한 (크루, 달)은 다시 저장하지 않아 제한 시작일이 밀리지 않는다
 * - 한 달 조회가 실패해도 다른 달은 저장한다
 * - 한 달 저장 중 DB 오류가 나도 다른 달은 저장한다
 * - 응답에 크루 ID나 미달 횟수가 빠진 원소는 건너뛴다
 * - 저장 중 유니크 충돌(서버 여러 대가 같은 날 저장)이 난 크루만 건너뛰고 나머지 크루는 저장한다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class CrewPenaltyRecordTest {
	// 한국 시간 2026-11-12 04:30
	private static final Instant NOW = Instant.parse("2026-11-11T19:30:00Z");
	private static final YearMonth THIS_MONTH = YearMonth.of(2026, 11);
	private static final YearMonth LAST_MONTH = YearMonth.of(2026, 10);

	@Autowired
	private CrewPenaltyRecordUseCase crewPenaltyRecordUseCase;
	@MockitoSpyBean
	private CrewPenaltyRepository crewPenaltyRepository;
	@MockitoBean
	private LowPaymentCrewPort lowPaymentCrewPort;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from member_crew_penalty");
	}

	@Test
	@DisplayName("이번 달과 지난달 미달 크루를 저장하고, 제한은 저장한 시각부터 30일이다")
	void recordsThisAndLastMonth() {
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH)).willReturn(List.of(new LowPaymentCrew(7L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of(new LowPaymentCrew(8L, 4)));

		int saved = crewPenaltyRecordUseCase.record(NOW);

		assertThat(saved).isEqualTo(2);
		LocalDateTime startAt = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
		assertThat(rows()).containsExactlyInAnyOrder(
			List.of(7L, "2026-11", 3, startAt, startAt.plusDays(30)),
			List.of(8L, "2026-10", 4, startAt, startAt.plusDays(30)));
	}

	@Test
	@DisplayName("달은 한국 시간으로 정한다: UTC 11/30 16:00은 한국 12/1이라 12월과 11월을 묻는다")
	void monthsInKoreanTime() {
		given(lowPaymentCrewPort.findLowPaymentCrews(YearMonth.of(2026, 12)))
			.willReturn(List.of(new LowPaymentCrew(7L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(YearMonth.of(2026, 11))).willReturn(List.of());

		crewPenaltyRecordUseCase.record(Instant.parse("2026-11-30T16:00:00Z"));

		assertThat(rows()).extracting(row -> row.get(1)).containsExactly("2026-12");
	}

	@Test
	@DisplayName("같은 (크루, 달)을 다음 날 다시 받아도 저장하지 않아 제한 시작일이 밀리지 않는다")
	void skipsExistingCrewMonth() {
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH)).willReturn(List.of(new LowPaymentCrew(7L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of());
		crewPenaltyRecordUseCase.record(NOW);

		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH)).willReturn(List.of(new LowPaymentCrew(7L, 4)));
		int saved = crewPenaltyRecordUseCase.record(NOW.plusSeconds(86_400));

		assertThat(saved).isZero();
		LocalDateTime startAt = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
		assertThat(rows()).containsExactly(List.of(7L, "2026-11", 3, startAt, startAt.plusDays(30)));
	}

	@Test
	@DisplayName("한 달 조회가 실패해도 다른 달은 저장한다")
	void oneMonthFailureDoesNotBlockOther() {
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH))
			.willThrow(new CrewPenaltyLookupFailedException("상품 서비스 응답 없음"));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of(new LowPaymentCrew(8L, 3)));

		int saved = crewPenaltyRecordUseCase.record(NOW);

		assertThat(saved).isEqualTo(1);
		assertThat(rows()).extracting(row -> row.get(0)).containsExactly(8L);
	}

	@Test
	@DisplayName("저장 중 유니크 충돌이 난 크루만 건너뛰고 다른 크루는 저장한다")
	void uniqueConflictSkipsOnlyThatCrew() {
		jdbcTemplate.update("""
			insert into member_crew_penalty
			(crew_id, penalty_month, low_payment_count, start_at, end_at, created_at, updated_at)
			values (7, '2026-11', 3, ?, ?, ?, ?)
			""", LocalDateTime.of(2026, 11, 11, 4, 30), LocalDateTime.of(2026, 12, 11, 4, 30),
			LocalDateTime.of(2026, 11, 11, 4, 30), LocalDateTime.of(2026, 11, 11, 4, 30));
		// 다른 서버가 확인과 저장 사이에 먼저 저장한 상황: 확인에서는 없다고 나온다
		doReturn(false).when(crewPenaltyRepository).existsByCrewIdAndPenaltyMonth(eq(7L), anyString());
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH))
			.willReturn(List.of(new LowPaymentCrew(7L, 3), new LowPaymentCrew(9L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of());

		int saved = crewPenaltyRecordUseCase.record(NOW);

		assertThat(saved).isEqualTo(1);
		assertThat(rows()).extracting(row -> row.get(0)).containsExactlyInAnyOrder(7L, 9L);
		// 먼저 저장된 행의 시작일은 그대로다
		assertThat(jdbcTemplate.queryForObject(
			"select start_at from member_crew_penalty where crew_id = 7", LocalDateTime.class))
			.isEqualTo(LocalDateTime.of(2026, 11, 11, 4, 30));
	}

	@Test
	@DisplayName("한 달 저장 중 DB 오류가 나도 다른 달은 저장한다")
	void dbErrorInOneMonthDoesNotBlockOther() {
		doThrow(new DataAccessResourceFailureException("DB 연결 끊김"))
			.when(crewPenaltyRepository).existsByCrewIdAndPenaltyMonth(eq(7L), eq("2026-11"));
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH)).willReturn(List.of(new LowPaymentCrew(7L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of(new LowPaymentCrew(8L, 3)));

		int saved = crewPenaltyRecordUseCase.record(NOW);

		assertThat(saved).isEqualTo(1);
		assertThat(rows()).extracting(row -> row.get(0)).containsExactly(8L);
	}

	@Test
	@DisplayName("응답에 크루 ID나 미달 횟수가 빠진 원소는 건너뛰고 나머지 크루는 저장한다")
	void skipsElementWithMissingValue() {
		given(lowPaymentCrewPort.findLowPaymentCrews(THIS_MONTH)).willReturn(List.of(
			new LowPaymentCrew(null, 3), new LowPaymentCrew(8L, null), new LowPaymentCrew(9L, 3)));
		given(lowPaymentCrewPort.findLowPaymentCrews(LAST_MONTH)).willReturn(List.of());

		int saved = crewPenaltyRecordUseCase.record(NOW);

		assertThat(saved).isEqualTo(1);
		assertThat(rows()).extracting(row -> row.get(0)).containsExactly(9L);
	}

	private List<List<Object>> rows() {
		return jdbcTemplate.query("""
				select crew_id, penalty_month, low_payment_count, start_at, end_at
				from member_crew_penalty order by crew_penalty_id
				""",
			(rs, rowNum) -> List.of(rs.getLong(1), rs.getString(2), rs.getInt(3),
				rs.getObject(4, LocalDateTime.class), rs.getObject(5, LocalDateTime.class)));
	}
}
