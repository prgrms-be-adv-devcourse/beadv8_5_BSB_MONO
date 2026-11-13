package com.bukang.inquiry.app;

import org.springframework.data.domain.Page;

import com.bukang.inquiry.domain.Inquiry;

// 판매자·관리자 목록 한 페이지와 상태별 개수. 닉네임은 부르는 쪽(InquiryFacade)이 트랜잭션 밖에서 채운다
public record InquiryList(Page<Inquiry> page, long waitingCount, long answeredCount) {
}
