package com.bukang.member.recordcert.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.member.recordcert.domain.JudgementReason;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;

/**
 * 내 제출 목록의 한 줄. 상태와 판정 사유만 보여 준다
 * 칸별 서버 확인 결과는 판매자 심사 화면에만 둔다. 조작하는 사람이 어느 칸이 들켰는지 배우지 못하게 (submit-spec 11/17)
 */
public record MyRecordCertSubmissionDto(
	long submissionId,
	long orderItemId,
	SubmissionStatus status,
	List<JudgementReason> reasons,
	LocalDateTime submittedAt
) {
	public static MyRecordCertSubmissionDto from(RecordCertSubmission submission) {
		return new MyRecordCertSubmissionDto(submission.getId(), submission.getOrderItemId(), submission.getStatus(),
			submission.judgementReasons(), submission.getCreatedAt());
	}
}
