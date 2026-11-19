package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.recordcert.event.RecordCertCheckRequestedEvent;
import com.bukang.member.app.MemberOutboxAppendUseCase;
import com.bukang.member.recordcert.domain.CertValues;
import com.bukang.member.recordcert.domain.CheckField;
import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.Roi;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertFileForbiddenException;
import com.bukang.member.recordcert.domain.exception.RecordCertFileNotFoundException;
import com.bukang.member.recordcert.domain.exception.RecordCertRuleNotFoundException;
import com.bukang.member.recordcert.domain.exception.RecordCertSubmissionInProgressException;
import com.bukang.member.recordcert.domain.exception.RecordCertSubmitLockedException;
import com.bukang.member.recordcert.domain.exception.RecordCertTicketForbiddenException;
import com.bukang.member.recordcert.domain.exception.RecordCertTicketNotFoundException;
import com.bukang.member.recordcert.domain.exception.RecordCertTicketNotPaidException;
import com.bukang.member.recordcert.dto.RecordCertSubmitRequestDto;
import com.bukang.member.recordcert.dto.RecordCertSubmittedDto;
import com.bukang.member.recordcert.out.CourseRulePort;
import com.bukang.member.recordcert.out.CourseRulePort.SellerCourseRule;
import com.bukang.member.recordcert.out.FileUploaderPort;
import com.bukang.member.recordcert.out.RecordCertRepository;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;
import com.bukang.member.recordcert.out.SubmissionLock;
import com.bukang.member.recordcert.out.TicketPort;
import com.bukang.member.recordcert.out.TicketPort.Ticket;

import lombok.RequiredArgsConstructor;

/**
 * 기록증 제출 (BUK-274, submit-spec 제출 흐름 1~7). 서버 확인 · 판정은 나중에 하고(BUK-275) 바로 확인 중으로 답한다
 * 참가권 단위 잠금 안에서: 참가권(order) → 올린 사람(file) → 종목 규칙(product) → 진행 중 제출 → 저장
 * 다른 서비스 호출은 트랜잭션 밖에서 하고(기다리는 동안 DB 연결을 잡지 않게), 저장과 아웃박스 두 메시지만 한 트랜잭션에 묶는다.
 * 그래서 제출이 남으면 확인 요청 · 파일 연결 요청도 함께 남고, 실패하면 셋 다 남지 않는다
 * 잠금 30초 > 다른 서비스 호출 세 번의 최대 기다림(연결 2초 + 응답 2초씩, 12초)이라 처리 중에 잠금이 풀리지 않는다
 */
@Service
@RequiredArgsConstructor
public class RecordCertSubmitUseCase {
	// 이 상태의 제출이 있으면 같은 참가권으로 다시 낼 수 없다. 반려 · 승인 불가 · 취소됨 뒤에만 다시 낸다 (11/17)
	private static final List<SubmissionStatus> BLOCKING = List.of(SubmissionStatus.CHECKING,
		SubmissionStatus.NEEDS_REVIEW, SubmissionStatus.AUTO_APPROVED, SubmissionStatus.APPROVED);
	private static final String RECORD_CERT = RecordCert.class.getSimpleName();

	private final SubmissionLock submissionLock;
	private final TicketPort ticketPort;
	private final FileUploaderPort fileUploaderPort;
	private final CourseRulePort courseRulePort;
	private final RecordCertRepository recordCertRepository;
	private final RecordCertSubmissionRepository recordCertSubmissionRepository;
	private final MemberOutboxAppendUseCase memberOutboxAppendUseCase;
	private final JsonConverter jsonConverter;
	private final TransactionTemplate transactionTemplate;

	public RecordCertSubmittedDto submit(long memberId, RecordCertSubmitRequestDto request) {
		long orderItemId = request.orderItemId();
		String token = submissionLock.tryLock(orderItemId).orElseThrow(RecordCertSubmitLockedException::new);
		try {
			Ticket ticket = checkTicket(memberId, orderItemId);
			checkFile(memberId, request.fileId());
			long sellerMemberId = checkRule(ticket.raceCourseId());
			return transactionTemplate.execute(status -> save(memberId, request, ticket, sellerMemberId));
		} finally {
			submissionLock.unlock(orderItemId, token);
		}
	}

	private Ticket checkTicket(long memberId, long orderItemId) {
		Ticket ticket = ticketPort.find(orderItemId).orElseThrow(RecordCertTicketNotFoundException::new);
		if (ticket.buyerId() != memberId) {
			throw new RecordCertTicketForbiddenException();
		}
		if (!ticket.isPaid()) {
			throw new RecordCertTicketNotPaidException();
		}
		return ticket;
	}

	private void checkFile(long memberId, long fileId) {
		long uploaderId = fileUploaderPort.findUploader(fileId).orElseThrow(RecordCertFileNotFoundException::new);
		if (uploaderId != memberId) {
			throw new RecordCertFileForbiddenException();
		}
	}

	// 규칙이 없는 종목은 서버 확인까지 가지 않는다. 돌려주는 판매자 회원 ID는 제출에 저장한다
	private long checkRule(long raceCourseId) {
		SellerCourseRule found = courseRulePort.find(raceCourseId);
		if (found.rule() == null) {
			throw new RecordCertRuleNotFoundException();
		}
		return found.sellerMemberId();
	}

	private RecordCertSubmittedDto save(long memberId, RecordCertSubmitRequestDto request, Ticket ticket,
		long sellerMemberId) {
		if (recordCertSubmissionRepository.existsByOrderItemIdAndStatusIn(request.orderItemId(), BLOCKING)) {
			throw new RecordCertSubmissionInProgressException();
		}
		LocalDateTime now = LocalDateTime.now();
		Map<CheckField, double[][]> boxes = request.fieldBoxes() == null
			? Map.of() : new EnumMap<>(request.fieldBoxes());
		RecordCertSubmitRequestDto.RoiDto roi = request.roi();
		RecordCert cert = recordCertRepository.save(RecordCert.create(memberId, request.fileId(),
			new Roi(roi.x(), roi.y(), roi.width(), roi.height()),
			new CertValues(request.netTimeSeconds(), request.raceDate(), request.courseClass(), request.bib()),
			jsonConverter.toJson(boxes), request.specVersion(), request.recModelSha256(), now));
		RecordCertSubmission submission = recordCertSubmissionRepository.save(RecordCertSubmission.submit(
			cert.getId(), memberId, request.orderItemId(), ticket.raceId(), ticket.raceCourseId(), sellerMemberId,
			now));

		memberOutboxAppendUseCase.appendRecordCertCheckRequested(RecordCertCheckRequestedEvent.of(submission.getId(),
			request.fileId(), byName(boxes), request.specVersion(), request.recModelSha256()));
		// 연결해야 file 정리 배치(올린 지 하루)가 사진을 지우지 않는다. 대상은 기록증, 소유자는 사진을 올린 제출 회원이다
		memberOutboxAppendUseCase.appendRecordCertFileLink(cert.getId(), new FileLinkRequestedEvent(memberId,
			List.of(new FileLinkRequestedEvent.Ref(RECORD_CERT, cert.getId(),
				List.of(new FileLinkRequestedEvent.Item(request.fileId(), FileType.RECORD_CERT, 0))))));
		return new RecordCertSubmittedDto(submission.getId(), submission.getStatus());
	}

	// common의 이벤트는 member의 CheckField를 모르므로 칸 이름을 키로 싣는다 (ai-service의 CertField와 이름이 같다)
	private static Map<String, double[][]> byName(Map<CheckField, double[][]> boxes) {
		Map<String, double[][]> named = new LinkedHashMap<>();
		boxes.forEach((field, box) -> named.put(field.name(), box));
		return named;
	}
}
