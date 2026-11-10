package com.bukang.inquiry.app;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.InquiryType;
import com.bukang.inquiry.domain.exception.InquiryAlreadyAnsweredException;
import com.bukang.inquiry.domain.exception.InquiryNotFoundException;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryPageDto;
import com.bukang.inquiry.out.InquiryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 문의 저장·조회·답변. 알림은 트랜잭션 안에서 발행하고, common의 NotificationKafkaSender가 커밋 뒤에만 Kafka로 보낸다
 * (롤백되면 보내지 않고, 보내기에 실패해도 문의·답변은 이미 저장돼 있다)
 * 관리자 확인·판매자 조회처럼 다른 서비스를 부르는 일은 InquiryFacade가 트랜잭션 밖에서 먼저 끝낸다
 */
@Service
@RequiredArgsConstructor
public class InquiryUseCase {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final InquiryRepository inquiryRepository;
	private final EventPublisher eventPublisher;

	// 판매자 담당 문의면 그 대회 판매자에게 알린다. 관리자 담당 문의는 알림이 없다
	@Transactional
	public InquiryDto create(long memberId, long sellerMemberId, long raceId, InquiryType type, String title,
		String body) {
		Inquiry inquiry = inquiryRepository.save(Inquiry.create(memberId, raceId, type, sellerMemberId, title, body));
		if (inquiry.getAssignee() == InquiryAssignee.SELLER) {
			eventPublisher.publish(NotificationRequestedEvent.of(sellerMemberId, NotificationKind.INQUIRY_RECEIVED,
				"새 문의가 왔어요", title + "\n문의에 답변해 주세요.", "/seller/inquiries/" + inquiry.getId()));
		}
		return InquiryDto.from(inquiry);
	}

	@Transactional(readOnly = true)
	public InquiryPageDto findMine(long memberId, int page, int size) {
		return InquiryPageDto.from(inquiryRepository.findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size))
			.map(InquiryDto::from));
	}

	// 남의 문의는 없는 문의와 같이 404로 답한다
	@Transactional(readOnly = true)
	public InquiryDto findMineOne(long memberId, long inquiryId) {
		return inquiryRepository.findById(inquiryId)
			.filter(inquiry -> inquiry.getMemberId() == memberId)
			.map(InquiryDto::from)
			.orElseThrow(InquiryNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public InquiryPageDto findForSeller(long sellerMemberId, int page, int size) {
		return InquiryPageDto.from(inquiryRepository
			.findBySellerMemberIdAndAssigneeOrderByIdDesc(sellerMemberId, InquiryAssignee.SELLER,
				PageRequest.of(page, size))
			.map(InquiryDto::from));
	}

	// 관리자는 담당과 상관없이 모든 문의를 본다 (판매자가 제때 답하는지 봐야 한다). 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional(readOnly = true)
	public InquiryPageDto findForAdmin(int page, int size) {
		return InquiryPageDto.from(inquiryRepository.findAllByOrderByIdDesc(PageRequest.of(page, size))
			.map(InquiryDto::from));
	}

	// 판매자는 자기 대회의 판매자 담당 문의에만 답한다. 아니면 없는 문의와 같이 404다
	@Transactional
	public InquiryDto answerBySeller(long sellerMemberId, long inquiryId, String content) {
		inquiryRepository.findById(inquiryId)
			.filter(inquiry -> inquiry.isSellerOwnedBy(sellerMemberId))
			.orElseThrow(InquiryNotFoundException::new);
		return answer(inquiryId, sellerMemberId, content);
	}

	// 관리자는 관리자 담당 문의에만 답한다. 판매자 담당 문의면 404다. 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional
	public InquiryDto answerByAdmin(long adminMemberId, long inquiryId, String content) {
		inquiryRepository.findById(inquiryId)
			.filter(Inquiry::isAdminOwned)
			.orElseThrow(InquiryNotFoundException::new);
		return answer(inquiryId, adminMemberId, content);
	}

	// 「답변 대기인 행만」 바꾸는 조건부 UPDATE라서, 둘이 동시에 답해도 하나만 저장된다 (바뀐 행이 0이면 이미 답한 문의)
	private InquiryDto answer(long inquiryId, long answeredBy, String content) {
		LocalDateTime now = LocalDateTime.now(KOREA);
		int updated = inquiryRepository.answerIfWaiting(inquiryId, content, answeredBy, now, InquiryStatus.ANSWERED,
			InquiryStatus.WAITING);
		if (updated == 0) {
			throw new InquiryAlreadyAnsweredException();
		}
		Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow(InquiryNotFoundException::new);
		eventPublisher.publish(NotificationRequestedEvent.of(inquiry.getMemberId(), NotificationKind.INQUIRY_ANSWERED,
			"문의에 답변이 달렸어요", inquiry.getTitle() + "\n답변을 확인해 보세요.", "/me/inquiries/" + inquiry.getId()));
		return InquiryDto.from(inquiry);
	}
}
