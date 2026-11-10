package com.bukang.inquiry.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
import com.bukang.inquiry.out.InquiryRepository.StatusCount;

import lombok.RequiredArgsConstructor;

/**
 * 문의 저장·조회·답변·고치기·지우기. 알림은 트랜잭션 안에서 발행하고, common의 NotificationKafkaSender가 커밋 뒤에만 Kafka로 보낸다
 * (롤백되면 보내지 않고, 보내기에 실패해도 문의·답변은 이미 저장돼 있다)
 * 관리자 확인·판매자 조회·주문 확인처럼 다른 서비스를 부르는 일은 InquiryFacade가 트랜잭션 밖에서 먼저 끝낸다
 * 답변·고치기·지우기는 「답변 대기이고 지우지 않은 행만」 바꾸는 조건부 UPDATE라서 겹쳐도 한쪽만 성공한다.
 * 바뀐 행이 0일 때 「이미 처리됨(409)」과 「없어짐(404)」을 가르려면 상대가 방금 커밋한 값을 읽어야 하므로,
 * MySQL 기본(REPEATABLE READ)의 스냅샷 읽기 대신 READ COMMITTED로 연다
 */
@Service
@RequiredArgsConstructor
public class InquiryUseCase {
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final InquiryRepository inquiryRepository;
	private final EventPublisher eventPublisher;

	// 판매자 담당 문의면 그 대회 판매자에게 알린다. 관리자 담당 문의는 알림이 없다
	@Transactional
	public InquiryDto create(long memberId, Long sellerMemberId, Long raceId, Long orderId, InquiryType type,
		String title, String body) {
		Inquiry inquiry = inquiryRepository.save(
			Inquiry.create(memberId, raceId, orderId, type, sellerMemberId, title, body));
		if (inquiry.getAssignee() == InquiryAssignee.SELLER) {
			eventPublisher.publish(NotificationRequestedEvent.of(sellerMemberId, NotificationKind.INQUIRY_RECEIVED,
				"새 문의가 왔어요", title + "\n문의에 답변해 주세요.", "/seller/inquiries/" + inquiry.getId()));
		}
		return InquiryDto.from(inquiry);
	}

	@Transactional(readOnly = true)
	public InquiryPageDto findMine(long memberId, int page, int size) {
		return InquiryPageDto.from(inquiryRepository
			.findByMemberIdAndDeletedAtIsNullOrderByIdDesc(memberId, PageRequest.of(page, size))
			.map(InquiryDto::from));
	}

	// 남의 문의·지운 문의는 없는 문의와 같이 404로 답한다
	@Transactional(readOnly = true)
	public InquiryDto findMineOne(long memberId, long inquiryId) {
		return InquiryDto.from(findMine(memberId, inquiryId));
	}

	// 판매자 목록: 답변 대기 먼저, 그 안에서 최신순. status를 주면 그 상태만 준다. 개수는 거르기와 상관없이 판매자 범위 전체다
	@Transactional(readOnly = true)
	public InquiryList findForSeller(long sellerMemberId, InquiryStatus status, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		var result = status == null
			? inquiryRepository.findBySellerMemberIdAndAssigneeAndDeletedAtIsNullOrderByStatusDescIdDesc(
				sellerMemberId, InquiryAssignee.SELLER, pageRequest)
			: inquiryRepository.findBySellerMemberIdAndAssigneeAndStatusAndDeletedAtIsNullOrderByIdDesc(
				sellerMemberId, InquiryAssignee.SELLER, status, pageRequest);
		return listOf(result, inquiryRepository.countBySeller(sellerMemberId, InquiryAssignee.SELLER));
	}

	// 관리자는 담당과 상관없이 모든 문의를 본다 (판매자가 제때 답하는지 봐야 한다). 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional(readOnly = true)
	public InquiryList findForAdmin(InquiryStatus status, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		var result = status == null
			? inquiryRepository.findByDeletedAtIsNullOrderByStatusDescIdDesc(pageRequest)
			: inquiryRepository.findByStatusAndDeletedAtIsNullOrderByIdDesc(status, pageRequest);
		return listOf(result, inquiryRepository.countAll());
	}

	// 판매자는 자기 대회의 판매자 담당 문의만 본다. 아니면 없는 문의와 같이 404다
	@Transactional(readOnly = true)
	public Inquiry findForSellerOne(long sellerMemberId, long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(inquiry -> inquiry.isSellerOwnedBy(sellerMemberId))
			.orElseThrow(InquiryNotFoundException::new);
	}

	// 관리자는 모든 문의의 상세를 본다. 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional(readOnly = true)
	public Inquiry findForAdminOne(long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId).orElseThrow(InquiryNotFoundException::new);
	}

	// 판매자는 자기 대회의 판매자 담당 문의에만 답한다. 아니면 없는 문의와 같이 404다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto answerBySeller(long sellerMemberId, long inquiryId, String content) {
		findForSellerOne(sellerMemberId, inquiryId);
		return answer(inquiryId, sellerMemberId, content);
	}

	// 관리자는 관리자 담당 문의에만 답한다. 판매자 담당 문의면 404다. 관리자 확인은 부르는 쪽이 끝냈다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto answerByAdmin(long adminMemberId, long inquiryId, String content) {
		inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(Inquiry::isAdminOwned)
			.orElseThrow(InquiryNotFoundException::new);
		return answer(inquiryId, adminMemberId, content);
	}

	// 보낸 회원이 답변 대기인 문의의 제목·본문을 고친다. 남의 문의면 404, 답변이 달렸으면 409다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public InquiryDto edit(long memberId, long inquiryId, String title, String body) {
		findMine(memberId, inquiryId);
		int updated = inquiryRepository.editIfWaiting(inquiryId, memberId, title, body, now(), InquiryStatus.WAITING);
		if (updated == 0) {
			throw lostRace(inquiryId);
		}
		return InquiryDto.from(inquiryRepository.findById(inquiryId).orElseThrow(InquiryNotFoundException::new));
	}

	// 보낸 회원이 답변 대기인 문의를 지운다. 지움 표시만 하고 행은 남긴다. 남의 문의면 404, 답변이 달렸으면 409다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(long memberId, long inquiryId) {
		findMine(memberId, inquiryId);
		int updated = inquiryRepository.deleteIfWaiting(inquiryId, memberId, now(), InquiryStatus.WAITING);
		if (updated == 0) {
			throw lostRace(inquiryId);
		}
	}

	private Inquiry findMine(long memberId, long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(inquiry -> inquiry.getMemberId() == memberId)
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
		eventPublisher.publish(NotificationRequestedEvent.of(inquiry.getMemberId(), NotificationKind.INQUIRY_ANSWERED,
			"문의에 답변이 달렸어요", inquiry.getTitle() + "\n답변을 확인해 보세요.", "/me/inquiries/" + inquiry.getId()));
		return InquiryDto.from(inquiry);
	}

	// 조건부 UPDATE가 0행일 때: 그 사이에 지워졌으면 404, 아직 있으면 이미 답변이 달린 것이라 409다
	private RuntimeException lostRace(long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId).isPresent()
			? new InquiryAlreadyAnsweredException()
			: new InquiryNotFoundException();
	}

	private static InquiryList listOf(org.springframework.data.domain.Page<Inquiry> page, List<StatusCount> counts) {
		long waiting = 0;
		long answered = 0;
		for (StatusCount count : counts) {
			if (count.getStatus() == InquiryStatus.WAITING) {
				waiting = count.getTotal();
			} else {
				answered = count.getTotal();
			}
		}
		return new InquiryList(page, waiting, answered);
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(KOREA);
	}
}
