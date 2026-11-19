package com.bukang.member.recordcert.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bukang.member.recordcert.domain.exception.SubmissionNotCheckingException;

// 제출 상태: 판정 × 서버 확인 → 자동 승인 · 반려 · 심사 필요 (BUK-273)
class RecordCertSubmissionTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 15, 0);
	private static final ServerCheck ALL_MATCH = serverCheck(FieldCheck.MATCH);
	private static final ServerCheck ONE_MISMATCH = new ServerCheck(Map.of(
		CheckField.NET_TIME, FieldCheck.MISMATCH,
		CheckField.NET_LABEL, FieldCheck.MATCH,
		CheckField.RACE_DATE, FieldCheck.MATCH,
		CheckField.COURSE, FieldCheck.MATCH));

	@Test
	@DisplayName("제출은 확인 중으로 만들어진다")
	void submitStartsChecking() {
		assertThat(newSubmission().getStatus()).isEqualTo(SubmissionStatus.CHECKING);
	}

	@ParameterizedTest(name = "판정 {0}, 서버 확인 {1} → {2}")
	@CsvSource({
		"PASS, true, AUTO_APPROVED",
		"PASS, false, NEEDS_REVIEW",
		"NEEDS_REVIEW, true, NEEDS_REVIEW",
		"NEEDS_REVIEW, false, NEEDS_REVIEW",
		"REJECT, true, REJECTED",
		"REJECT, false, REJECTED"
	})
	@DisplayName("판정과 서버 확인 결과로 제출 상태가 정해진다")
	void applyCheckDecidesStatus(Verdict verdict, boolean serverMatches, SubmissionStatus expected) {
		RecordCertSubmission submission = newSubmission();

		submission.applyCheck(new Judgement(verdict, List.of()), serverMatches ? ALL_MATCH : ONE_MISMATCH, NOW);

		assertThat(submission.getStatus()).isEqualTo(expected);
	}

	@Test
	@DisplayName("판정 사유와 칸별 서버 확인 결과를 남긴다")
	void keepsReasonsAndFieldChecks() {
		RecordCertSubmission submission = newSubmission();

		Judgement tooFast = new Judgement(Verdict.NEEDS_REVIEW, List.of(JudgementReason.TOO_FAST));

		submission.applyCheck(tooFast, ONE_MISMATCH, NOW);

		assertThat(submission.judgementReasons()).containsExactly(JudgementReason.TOO_FAST);
		assertThat(submission.serverCheck().results()).isEqualTo(ONE_MISMATCH.results());
	}

	@Test
	@DisplayName("확인 중이 아닌 제출에는 결과를 다시 반영하지 않는다")
	void applyCheckOnlyOnce() {
		RecordCertSubmission submission = newSubmission();
		submission.applyCheck(new Judgement(Verdict.PASS, List.of()), ALL_MATCH, NOW);

		assertThatThrownBy(() -> submission.applyCheck(new Judgement(Verdict.REJECT, List.of()), ALL_MATCH, NOW))
			.isInstanceOf(SubmissionNotCheckingException.class);
		assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.AUTO_APPROVED);
	}

	private static RecordCertSubmission newSubmission() {
		return RecordCertSubmission.submit(1L, 10L, 100L, 1000L, 2000L, 9L, NOW);
	}

	private static ServerCheck serverCheck(FieldCheck all) {
		return new ServerCheck(Map.of(
			CheckField.NET_TIME, all, CheckField.NET_LABEL, all, CheckField.RACE_DATE, all, CheckField.COURSE, all));
	}
}
