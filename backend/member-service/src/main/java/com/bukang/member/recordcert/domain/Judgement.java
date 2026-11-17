package com.bukang.member.recordcert.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 판정: 기록증 규칙 × 참가자가 확정한 값. today는 기간 경계 테스트를 위해 받는다
// 판정 규칙 J1~J7: 반려 사유가 있으면 반려, 심사 필요 사유만 있으면 심사 필요, 없으면 통과
public record Judgement(Verdict verdict, List<JudgementReason> reasons) {
	// 넷타임 · 대회일 · 종목은 제출에서 필수라 null이 아니라고 가정한다
	public static Judgement of(CourseRule rule, CertValues values, LocalDate today) {
		List<JudgementReason> reasons = new ArrayList<>();
		if (values.raceDate().isBefore(rule.recognizedSince())) {
			reasons.add(JudgementReason.BEFORE_RECOGNIZED_PERIOD);
		}
		if (values.raceDate().isAfter(today)) {
			reasons.add(JudgementReason.FUTURE_RACE_DATE);
		}
		if (values.netSeconds() > rule.maxNetSeconds()) {
			reasons.add(JudgementReason.NET_TIME_OVER);
		}
		if (values.courseClass().isTooFast(values.netSeconds())) {
			reasons.add(JudgementReason.TOO_FAST);
		}
		if (values.courseClass() == CourseClass.OTHER) {
			reasons.add(JudgementReason.COURSE_OTHER);
		} else if (!rule.allowedClasses().contains(values.courseClass())) {
			reasons.add(JudgementReason.COURSE_NOT_ALLOWED);
		}
		return new Judgement(verdictOf(reasons), List.copyOf(reasons));
	}

	private static Verdict verdictOf(List<JudgementReason> reasons) {
		if (reasons.stream().anyMatch(JudgementReason::rejects)) {
			return Verdict.REJECT;
		}
		return reasons.isEmpty() ? Verdict.PASS : Verdict.NEEDS_REVIEW;
	}
}
