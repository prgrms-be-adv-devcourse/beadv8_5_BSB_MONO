package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;
import com.bukang.member.recordcert.domain.CertValues;
import com.bukang.member.recordcert.domain.CheckField;
import com.bukang.member.recordcert.domain.CourseRule;
import com.bukang.member.recordcert.domain.Judgement;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.ServerCheck;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertRuleNotFoundException;
import com.bukang.member.recordcert.out.CourseRulePort;
import com.bukang.member.recordcert.out.RecordCertRepository;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서버 확인 결과를 제출에 반영한다 (BUK-275, server-check-spec 흐름 4): 서버 확인 비교 → 종목 규칙(product) → 판정 → 상태
 * 확인 중이 아닌 제출(같은 결과를 두 번 받음, 환불로 취소됨)은 무시한다. 그래서 인박스를 두지 않는다
 * product 조회는 트랜잭션 밖에서 한다. 그 사이 다른 쪽(판매자 심사 · 환불 닫기)이 제출을 바꾸면 저장할 때
 * 낙관적 잠금(@Version)으로 실패하고, 리스너가 다시 시도해 바뀐 상태를 보고 무시한다
 * product가 응답하지 않으면 예외(503)를 그대로 던져 다시 시도하게 한다. 끝내 실패하면 실패 토픽 처리가 심사 필요로 넘긴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCertCheckResultUseCase {
	private final RecordCertSubmissionRepository recordCertSubmissionRepository;
	private final RecordCertRepository recordCertRepository;
	private final CourseRulePort courseRulePort;

	public void apply(RecordCertCheckedEvent result) {
		RecordCertSubmission submission = recordCertSubmissionRepository.findById(result.submissionId()).orElse(null);
		if (submission == null || submission.getStatus() != SubmissionStatus.CHECKING) {
			log.info("확인 중이 아닌 제출이라 서버 확인 결과를 무시합니다. submissionId={}, status={}",
				result.submissionId(), submission == null ? null : submission.getStatus());
			return;
		}
		if (result.failReason() != null) {
			log.warn("서버가 기록증을 읽지 못한 결과입니다. 모든 칸 읽지 못함으로 판정합니다. submissionId={}, 이유={}",
				result.submissionId(), result.failReason());
		}
		CertValues values = recordCertRepository.findById(submission.getRecordCertId()).orElseThrow().values();
		ServerCheck serverCheck = ServerCheck.of(values, texts(result.texts()));
		// 규칙 없는 종목은 제출 때 막으므로 여기서는 있다고 본다(server-check-spec). 없으면 실패 토픽 → 심사 필요
		CourseRule rule = courseRulePort.find(submission.getRaceCourseId()).rule();
		if (rule == null) {
			throw new RecordCertRuleNotFoundException();
		}
		LocalDateTime now = LocalDateTime.now();
		submission.applyCheck(Judgement.of(rule, values, now.toLocalDate()), serverCheck, now);
		// 트랜잭션 밖에서 읽은 제출이라 save가 merge한다. 그 사이 version이 바뀌었으면 낙관적 잠금 예외가 난다
		recordCertSubmissionRepository.save(submission);
	}

	private static Map<CheckField, String> texts(Map<String, String> named) {
		Map<CheckField, String> texts = new EnumMap<>(CheckField.class);
		if (named != null) {
			named.forEach((name, text) -> texts.put(CheckField.valueOf(name), text));
		}
		return texts;
	}
}
