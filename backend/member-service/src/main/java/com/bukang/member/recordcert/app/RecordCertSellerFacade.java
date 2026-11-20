package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.member.recordcert.domain.DenyReason;
import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertSubmissionNotFoundException;
import com.bukang.member.recordcert.dto.RecordCertReviewCountDto;
import com.bukang.member.recordcert.dto.SellerRecordCertDetailDto;
import com.bukang.member.recordcert.dto.SellerRecordCertSubmissionDto;
import com.bukang.member.recordcert.out.FileViewUrlPort;
import com.bukang.member.recordcert.out.RecordCertReviewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 기록증 심사 API (BUK-276): 대회별 목록 · 상세 · 심사 · 심사 필요 건수
 * 조회는 트랜잭션을 열지 않는다. 상세가 파일 서비스를 부르는 동안 DB 연결을 잡지 않게
 */
@Service
@RequiredArgsConstructor
public class RecordCertSellerFacade {
	private final RecordCertSellerAccess access;
	private final RecordCertReviewUseCase recordCertReviewUseCase;
	private final RecordCertReviewRepository recordCertReviewRepository;
	private final FileViewUrlPort fileViewUrlPort;

	// 제출 0건이면 내 대회인지 알 수 없어 빈 목록 (11/18). 제출이 있으면 판매자 회원 ID로 소유를 본다
	// shortcut: 대회의 제출을 모두 읽어 메모리에서 거른다(페이지 없음). 대회 하나가 수천 건을 넘으면 쿼리 필터 · 페이지로 바꾼다
	public List<SellerRecordCertSubmissionDto> findRaceSubmissions(long memberId, long raceId,
		SubmissionStatus status, Boolean serverCheckMatched) {
		access.requireApprovedSeller(memberId);
		List<RecordCertSubmission> submissions = recordCertReviewRepository.findByRaceIdOrderByIdDesc(raceId);
		if (submissions.isEmpty()) {
			return List.of();
		}
		access.requireOwner(memberId, submissions.get(0));
		List<RecordCertSubmission> filtered = submissions.stream()
			.filter(s -> status == null || s.getStatus() == status)
			.filter(s -> serverCheckMatched == null || s.serverCheck().allMatch() == serverCheckMatched)
			.toList();
		Map<Long, RecordCert> certs = certsOf(filtered);
		return filtered.stream()
			.map(s -> SellerRecordCertSubmissionDto.of(s, certs.get(s.getRecordCertId())))
			.toList();
	}

	public SellerRecordCertDetailDto findDetail(long memberId, long submissionId) {
		access.requireApprovedSeller(memberId);
		RecordCertSubmission submission = recordCertReviewRepository.findById(submissionId)
			.orElseThrow(RecordCertSubmissionNotFoundException::new);
		access.requireOwner(memberId, submission);
		RecordCert cert = certsOf(List.of(submission)).get(submission.getRecordCertId());
		// 사진 정리 배치가 지운 건(file_deleted_at)은 file을 부르지 않는다. 안 그러면 없는 파일이 503으로 보인다 (리뷰 #646)
		String fileUrl = cert.getFileDeletedAt() != null ? null : fileViewUrlPort.viewUrl(cert.getFileId());
		return SellerRecordCertDetailDto.of(submission, cert, fileUrl);
	}

	public SellerRecordCertSubmissionDto approve(long memberId, long submissionId) {
		recordCertReviewUseCase.approve(memberId, submissionId, LocalDateTime.now());
		return reviewed(memberId, submissionId);
	}

	public SellerRecordCertSubmissionDto deny(long memberId, long submissionId, DenyReason reason, String memo) {
		recordCertReviewUseCase.deny(memberId, submissionId, reason, memo, LocalDateTime.now());
		return reviewed(memberId, submissionId);
	}

	public RecordCertReviewCountDto countWaiting(long memberId) {
		access.requireApprovedSeller(memberId);
		return new RecordCertReviewCountDto(
			recordCertReviewRepository.countBySellerMemberIdAndStatus(memberId, SubmissionStatus.NEEDS_REVIEW));
	}

	private SellerRecordCertSubmissionDto reviewed(long memberId, long submissionId) {
		RecordCertSubmission submission = recordCertReviewRepository.findById(submissionId)
			.orElseThrow(RecordCertSubmissionNotFoundException::new);
		RecordCert cert = certsOf(List.of(submission)).get(submission.getRecordCertId());
		return SellerRecordCertSubmissionDto.of(submission, cert);
	}

	private Map<Long, RecordCert> certsOf(List<RecordCertSubmission> submissions) {
		List<Long> ids = submissions.stream().map(RecordCertSubmission::getRecordCertId).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return recordCertReviewRepository.findCertsByIdIn(ids).stream()
			.collect(Collectors.toMap(RecordCert::getId, Function.identity()));
	}
}
