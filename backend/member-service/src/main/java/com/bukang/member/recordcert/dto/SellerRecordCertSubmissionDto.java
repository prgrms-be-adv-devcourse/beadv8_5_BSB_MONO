package com.bukang.member.recordcert.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.bukang.member.recordcert.domain.CheckField;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.DenyReason;
import com.bukang.member.recordcert.domain.FieldCheck;
import com.bukang.member.recordcert.domain.JudgementReason;
import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

// 판매자 심사 목록의 한 줄 (BUK-276). 판매자에게는 칸별 서버 확인 결과까지 보인다
public record SellerRecordCertSubmissionDto(
	@Schema(description = "제출 ID", example = "31")
	long submissionId,
	@Schema(description = "참가권(주문 항목) ID", example = "700")
	long orderItemId,
	@Schema(description = "종목 ID", example = "70")
	long raceCourseId,
	@Schema(description = "제출 상태")
	SubmissionStatus status,
	@Schema(description = "판정 사유")
	List<JudgementReason> reasons,
	@Schema(description = "칸별 서버 확인 결과. 확인 중이면 빈 객체")
	Map<CheckField, FieldCheck> serverCheck,
	@Schema(description = "네 칸이 모두 맞음이면 true", example = "false")
	boolean serverCheckMatched,
	@Schema(description = "참가자가 확정한 넷타임(초)", example = "5405")
	int netTimeSeconds,
	@Schema(description = "참가자가 확정한 대회일", example = "2026-04-12")
	LocalDate raceDate,
	@Schema(description = "참가자가 확정한 종목 구분")
	CourseClass courseClass,
	@Schema(description = "승인 불가 사유. 승인 불가일 때만")
	DenyReason denyReason,
	@Schema(description = "승인 불가 메모. 승인 불가이고 메모를 남겼을 때만", example = "사진 속 이름이 참가자와 달라요")
	String denyMemo,
	@Schema(description = "제출 시각", example = "2026-11-18T09:00:00")
	LocalDateTime submittedAt,
	@Schema(description = "심사 시각. 판매자가 심사한 뒤에만", example = "2026-11-18T14:00:00")
	LocalDateTime reviewedAt
) {
	public static SellerRecordCertSubmissionDto of(RecordCertSubmission submission, RecordCert cert) {
		return new SellerRecordCertSubmissionDto(submission.getId(), submission.getOrderItemId(),
			submission.getRaceCourseId(), submission.getStatus(), submission.judgementReasons(),
			submission.serverCheck().results(), submission.serverCheck().allMatch(), cert.getNetTimeSeconds(),
			cert.getRaceDate(), cert.getCourseClass(), submission.getDenyReason(), submission.getDenyMemo(),
			submission.getCreatedAt(), submission.getReviewedAt());
	}
}
