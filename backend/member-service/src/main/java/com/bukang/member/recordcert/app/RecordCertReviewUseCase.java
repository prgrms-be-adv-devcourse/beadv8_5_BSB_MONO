package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.recordcert.domain.DenyReason;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.exception.RecordCertSubmissionNotFoundException;
import com.bukang.member.recordcert.domain.exception.SubmissionNotReviewableException;
import com.bukang.member.recordcert.out.RecordCertReviewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자가 심사 필요 제출을 승인 · 승인 불가로 정한다 (BUK-276)
 * 같은 제출을 동시에 바꾸면(환불 닫기 · 두 번 누름) 낙관적 잠금으로 늦게 저장한 쪽이 409가 된다 (judgement-spec 3장, 11/18)
 */
@Service
@RequiredArgsConstructor
public class RecordCertReviewUseCase {
	private final RecordCertSellerAccess access;
	private final RecordCertReviewRepository recordCertReviewRepository;

	@Transactional
	public void approve(long memberId, long submissionId, LocalDateTime now) {
		review(memberId, submissionId, submission -> submission.approve(now));
	}

	@Transactional
	public void deny(long memberId, long submissionId, DenyReason reason, String memo, LocalDateTime now) {
		review(memberId, submissionId, submission -> submission.deny(reason, memo, now));
	}

	private void review(long memberId, long submissionId, Consumer<RecordCertSubmission> decision) {
		access.requireApprovedSeller(memberId);
		RecordCertSubmission submission = recordCertReviewRepository.findById(submissionId)
			.orElseThrow(RecordCertSubmissionNotFoundException::new);
		access.requireOwner(memberId, submission);
		decision.accept(submission);
		try {
			recordCertReviewRepository.flush();
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new SubmissionNotReviewableException();
		}
	}
}
