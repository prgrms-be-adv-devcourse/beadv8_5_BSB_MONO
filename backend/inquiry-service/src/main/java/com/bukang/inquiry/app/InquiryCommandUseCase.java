package com.bukang.inquiry.app;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryDraft;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.exception.InquiryAlreadyAnsweredException;
import com.bukang.inquiry.domain.exception.InquiryNotFoundException;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.out.InquiryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 문의 저장·답변·고치기·지우기. 알림은 트랜잭션 안에서 발행하고, common의 NotificationKafkaSender가 커밋 뒤에만 Kafka로 보낸다
 * (롤백되면 보내지 않고, 보내기에 실패해도 문의·답변은 이미 저장돼 있다)
 * 관리자 확인·판매자 조회·주문 확인처럼 다른 서비스를 부르는 일은 InquiryCreateFacade·InquiryManageFacade가 트랜잭션 밖에서 먼저 끝낸다
 * 답변·고치기·지우기는 「답변 대기이고 지우지 않은 행만」 바꾸는 조건부 UPDATE라서 겹쳐도 한쪽만 성공한다.
 * 바뀐 행이 0일 때 「이미 처리됨(409)」과 「없어짐(404)」을 가르려면 상대가 방금 커밋한 값을 읽어야 하므로,
 * MySQL 기본(REPEATABLE READ)의 스냅샷 읽기 대신 READ COMMITTED로 연다
 * MySQL은 READ COMMITTED에서 쓰려면 binlog_format=ROW가 필요하다 (MySQL 8 기본값). 서버 설정을 STATEMENT로 바꾸면 쓰기가 실패한다
 */
@Service
@RequiredArgsConstructor
public class InquiryCommandUseCase {
	private final InquiryRepository inquiryRepository;
	private final EventPublisher eventPublisher;
	private final Clock clock;

	// 판매자 담당 문의면 그 대회 판매자에게 알린다. 관리자 담당 문의는 알림이 없다
	@Transactional
	public InquiryDto create(InquiryDraft draft) {
		Inquiry inquiry = inquiryRepository.save(Inquiry.create(draft));
		if (inquiry.isSellerAssigned()) {
			eventPublisher.publish(InquiryNotifications.received(inquiry));
		}
		return InquiryDto.from(inquiry);
	}

	// 판매자는 자기 대회의 판매자 담당 문의에만 답한다. 아니면 없는 문의와 같이 404다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto answerBySeller(long sellerMemberId, long inquiryId, String content) {
		requireSellerOwned(sellerMemberId, inquiryId);
		return answer(inquiryId, sellerMemberId, content);
	}

	// 관리자는 관리자 담당 문의에만 답한다. 판매자 담당 문의면 404다. 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto answerByAdmin(long adminMemberId, long inquiryId, String content) {
		requireActive(inquiryId, Inquiry::isAdminOwned);
		return answer(inquiryId, adminMemberId, content);
	}

	// 보낸 회원이 답변 대기인 문의의 제목·본문을 고친다. 남의 문의면 404, 답변이 달렸으면 409다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto edit(long memberId, long inquiryId, String title, String body) {
		requireOwned(memberId, inquiryId);
		int updated = inquiryRepository.editIfWaiting(inquiryId, memberId, title, body, now(), InquiryStatus.WAITING);
		if (updated == 0) {
			throw lostRace(inquiryId);
		}
		return InquiryDto.from(inquiryRepository.findById(inquiryId).orElseThrow(InquiryNotFoundException::new));
	}

	// 보낸 회원이 답변 대기인 문의를 지운다. 지움 표시만 하고 행은 남긴다. 남의 문의면 404, 답변이 달렸으면 409다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(long memberId, long inquiryId) {
		requireOwned(memberId, inquiryId);
		int updated = inquiryRepository.deleteIfWaiting(inquiryId, memberId, now(), InquiryStatus.WAITING);
		if (updated == 0) {
			throw lostRace(inquiryId);
		}
	}

	private void requireOwned(long memberId, long inquiryId) {
		requireActive(inquiryId, inquiry -> inquiry.isOwnedBy(memberId));
	}

	private void requireSellerOwned(long sellerMemberId, long inquiryId) {
		requireActive(inquiryId, inquiry -> inquiry.isSellerOwnedBy(sellerMemberId));
	}

	// 지운 문의이거나 조건에 맞지 않으면 없는 문의와 같이 404다
	private void requireActive(long inquiryId, Predicate<Inquiry> condition) {
		inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(condition)
			.orElseThrow(InquiryNotFoundException::new);
	}

	// 「답변 대기이고 지우지 않은 행만」 바꾸는 조건부 UPDATE라서, 겹쳐도 하나만 저장된다
	private InquiryDto answer(long inquiryId, long answeredBy, String content) {
		int updated = inquiryRepository.answerIfWaiting(inquiryId, content, answeredBy, now(),
			InquiryStatus.ANSWERED, InquiryStatus.WAITING);
		if (updated == 0) {
			throw lostRace(inquiryId);
		}
		Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow(InquiryNotFoundException::new);
		eventPublisher.publish(InquiryNotifications.answered(inquiry));
		return InquiryDto.from(inquiry);
	}

	// 조건부 UPDATE가 0행일 때: 그 사이에 지워졌으면 404, 아직 있으면 이미 답변이 달린 것이라 409다
	private RuntimeException lostRace(long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId).isPresent()
			? new InquiryAlreadyAnsweredException()
			: new InquiryNotFoundException();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
