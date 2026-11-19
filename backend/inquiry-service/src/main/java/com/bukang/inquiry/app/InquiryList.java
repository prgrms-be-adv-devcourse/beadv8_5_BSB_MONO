package com.bukang.inquiry.app;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.dto.InquiryDto;

// 판매자·관리자 목록 한 페이지와 상태별 개수. 닉네임은 부르는 쪽(InquiryManageFacade)이 트랜잭션 밖에서 채운다
public record InquiryList(Page<Inquiry> page, long waitingCount, long answeredCount) {
	public Set<Long> memberIds() {
		return page.getContent().stream().map(Inquiry::getMemberId).collect(Collectors.toSet());
	}

	public Page<InquiryDto> mapPage(Function<Inquiry, InquiryDto> mapper) {
		return page.map(mapper);
	}
}
