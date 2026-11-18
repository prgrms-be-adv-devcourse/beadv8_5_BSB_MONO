package com.bukang.inquiry.app;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;
import com.bukang.inquiry.domain.exception.InquiryNotFoundException;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryPageDto;
import com.bukang.inquiry.out.InquiryRepository;
import com.bukang.inquiry.out.InquiryRepository.StatusCount;

import lombok.RequiredArgsConstructor;

/**
 * 문의 목록·상세 조회. 지운 문의는 어느 조회에서도 보이지 않는다
 * 관리자 확인·닉네임·주문 정보처럼 다른 서비스를 부르는 일은 InquiryManageFacade가 트랜잭션 밖에서 한다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryQuery {
	private final InquiryRepository inquiryRepository;

	public InquiryPageDto findMine(long memberId, int page, int size) {
		return InquiryPageDto.from(inquiryRepository
			.findByMemberIdAndDeletedAtIsNullOrderByIdDesc(memberId, PageRequest.of(page, size))
			.map(InquiryDto::from));
	}

	// 남의 문의·지운 문의는 없는 문의와 같이 404로 답한다
	public InquiryDto findMineOne(long memberId, long inquiryId) {
		return InquiryDto.from(inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(inquiry -> inquiry.isOwnedBy(memberId))
			.orElseThrow(InquiryNotFoundException::new));
	}

	// 판매자 목록: 답변 대기 먼저, 그 안에서 최신순. status를 주면 그 상태만 준다. 개수는 거르기와 상관없이 판매자 범위 전체다
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
	// assignee·status는 선택이고 함께 쓸 수 있다. 개수는 assignee를 따르고 status와는 상관없다
	public InquiryList findForAdmin(InquiryAssignee assignee, InquiryStatus status, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		if (assignee == null) {
			var result = status == null
				? inquiryRepository.findByDeletedAtIsNullOrderByStatusDescIdDesc(pageRequest)
				: inquiryRepository.findByStatusAndDeletedAtIsNullOrderByIdDesc(status, pageRequest);
			return listOf(result, inquiryRepository.countAll());
		}
		var result = status == null
			? inquiryRepository.findByAssigneeAndDeletedAtIsNullOrderByStatusDescIdDesc(assignee, pageRequest)
			: inquiryRepository.findByAssigneeAndStatusAndDeletedAtIsNullOrderByIdDesc(assignee, status, pageRequest);
		return listOf(result, inquiryRepository.countByAssignee(assignee));
	}

	// 판매자는 자기 대회의 판매자 담당 문의만 본다. 아니면 없는 문의와 같이 404다
	public Inquiry findForSellerOne(long sellerMemberId, long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId)
			.filter(inquiry -> inquiry.isSellerOwnedBy(sellerMemberId))
			.orElseThrow(InquiryNotFoundException::new);
	}

	// 관리자는 모든 문의의 상세를 본다. 관리자 확인은 부르는 쪽이 끝냈다
	public Inquiry findForAdminOne(long inquiryId) {
		return inquiryRepository.findByIdAndDeletedAtIsNull(inquiryId).orElseThrow(InquiryNotFoundException::new);
	}

	private static InquiryList listOf(Page<Inquiry> page, List<StatusCount> counts) {
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
}
