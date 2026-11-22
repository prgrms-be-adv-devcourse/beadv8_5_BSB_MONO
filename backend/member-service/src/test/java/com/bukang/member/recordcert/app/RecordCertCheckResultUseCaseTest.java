package com.bukang.member.recordcert.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;
import com.bukang.member.recordcert.domain.CertValues;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.CourseRule;
import com.bukang.member.recordcert.domain.FieldCheck;
import com.bukang.member.recordcert.domain.JudgementReason;
import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.Roi;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;
import com.bukang.member.recordcert.out.CourseRulePort;
import com.bukang.member.recordcert.out.CourseRulePort.SellerCourseRule;
import com.bukang.member.recordcert.out.RecordCertRepository;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;

/**
 * 서버 확인 결과 처리 (BUK-275, server-check-spec Testing Decisions의 심).
 * 종목 규칙 포트만 목으로 두고, 결과 하나를 처리한 뒤 저장된 제출 상태만 본다
 */
@SpringBootTest
@ActiveProfiles("test")
class RecordCertCheckResultUseCaseTest {
	private static final long RACE_COURSE_ID = 12;
	// 넷타임 2:46:40, 2025-10-04 풀
	private static final CertValues VALUES = new CertValues(10_000, LocalDate.of(2025, 10, 4), CourseClass.FULL, null);
	private static final CourseRule RULE = new CourseRule(LocalDate.of(2025, 1, 1), 3 * 3600,
		Set.of(CourseClass.FULL));
	private static final Map<String, String> ALL_MATCH = Map.of(
		"NET_TIME", "02:46:40",
		"NET_LABEL", "넷타임",
		"RACE_DATE", "2025.10.04",
		"COURSE", "풀코스");

	@Autowired
	private RecordCertCheckResultUseCase useCase;
	@Autowired
	private RecordCertRepository recordCertRepository;
	@Autowired
	private RecordCertSubmissionRepository recordCertSubmissionRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private CourseRulePort courseRulePort;

	@AfterEach
	void tearDown() {
		recordCertSubmissionRepository.deleteAll();
		recordCertRepository.deleteAll();
	}

	@Test
	@DisplayName("판정 통과이고 네 칸이 모두 맞으면 자동 승인")
	void autoApprovesWhenPassAndAllMatch() {
		long id = submission();
		rule(RULE);

		useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH));

		RecordCertSubmission saved = saved(id);
		assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.AUTO_APPROVED);
		assertThat(saved.serverCheck().allMatch()).isTrue();
	}

	@Test
	@DisplayName("판정이 반려면 서버 확인과 상관없이 반려")
	void rejectsWhenJudgementRejects() {
		long id = submission();
		rule(new CourseRule(LocalDate.of(2025, 1, 1), 9_000, Set.of(CourseClass.FULL)));

		useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH));

		RecordCertSubmission saved = saved(id);
		assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
		assertThat(saved.judgementReasons()).containsExactly(JudgementReason.NET_TIME_OVER);
	}

	@Test
	@DisplayName("판정 통과여도 한 칸이 다르면 심사 필요")
	void needsReviewWhenFieldMismatch() {
		long id = submission();
		rule(RULE);

		useCase.apply(RecordCertCheckedEvent.read(id, Map.of(
			"NET_TIME", "02:50:00", "NET_LABEL", "넷타임", "RACE_DATE", "2025.10.04", "COURSE", "풀코스")));

		RecordCertSubmission saved = saved(id);
		assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.NEEDS_REVIEW);
		assertThat(saved.getNetTimeCheck()).isEqualTo(FieldCheck.MISMATCH);
	}

	@Test
	@DisplayName("끝내 읽지 못한 결과는 모든 칸 읽지 못함으로 심사 필요")
	void failedResultNeedsReview() {
		long id = submission();
		rule(RULE);

		useCase.apply(RecordCertCheckedEvent.failed(id, "재시도 끝"));

		RecordCertSubmission saved = saved(id);
		assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.NEEDS_REVIEW);
		assertThat(saved.serverCheck().results().values()).hasSize(4).containsOnly(FieldCheck.UNREADABLE);
	}

	@Test
	@DisplayName("같은 결과를 두 번 받으면 두 번째는 무시한다")
	void ignoresDuplicateResult() {
		long id = submission();
		rule(RULE);
		useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH));

		useCase.apply(RecordCertCheckedEvent.failed(id, "재시도 끝"));

		assertThat(saved(id).getStatus()).isEqualTo(SubmissionStatus.AUTO_APPROVED);
	}

	@Test
	@DisplayName("취소된 제출의 결과는 무시한다")
	void ignoresCanceledSubmission() {
		long id = submission();
		rule(RULE);
		jdbcTemplate.update("update member_record_cert_submission set status = 'CANCELED' where submission_id = ?",
			id);

		useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH));

		assertThat(saved(id).getStatus()).isEqualTo(SubmissionStatus.CANCELED);
	}

	@Test
	@DisplayName("product가 응답하지 않으면 예외를 던져 재시도하게 하고 확인 중으로 둔다")
	void throwsWhenProductUnavailable() {
		long id = submission();
		willThrow(new RecordCertCheckUnavailableException("product")).given(courseRulePort).find(RACE_COURSE_ID);

		assertThatThrownBy(() -> useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH)))
			.isInstanceOf(RecordCertCheckUnavailableException.class);
		assertThat(saved(id).getStatus()).isEqualTo(SubmissionStatus.CHECKING);
	}

	@Test
	@DisplayName("규칙을 묻는 사이 제출이 취소되면 낙관적 잠금으로 실패해 재시도하게 한다")
	void optimisticLockFailsWhenCanceledMeanwhile() {
		long id = submission();
		// 규칙 조회 중에 환불 닫기(BUK-277)가 먼저 저장한 것처럼 상태와 version을 바꾼다
		willAnswer(invocation -> {
			jdbcTemplate.update("update member_record_cert_submission set status = 'CANCELED', version = version + 1"
				+ " where submission_id = ?", id);
			return new SellerCourseRule(9, RULE);
		}).given(courseRulePort).find(RACE_COURSE_ID);

		assertThatThrownBy(() -> useCase.apply(RecordCertCheckedEvent.read(id, ALL_MATCH)))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(saved(id).getStatus()).isEqualTo(SubmissionStatus.CANCELED);
	}

	private void rule(CourseRule rule) {
		willReturn(new SellerCourseRule(9, rule)).given(courseRulePort).find(RACE_COURSE_ID);
	}

	private long submission() {
		LocalDateTime now = LocalDateTime.now();
		RecordCert cert = recordCertRepository.save(RecordCert.create(3, 7, new Roi(0, 0, 10, 10), VALUES, "{}",
			"1", "sha", now));
		return recordCertSubmissionRepository.save(RecordCertSubmission.submit(cert.getId(), 3, 42, 11,
			RACE_COURSE_ID, 9, now)).getId();
	}

	private RecordCertSubmission saved(long id) {
		return recordCertSubmissionRepository.findById(id).orElseThrow();
	}
}
