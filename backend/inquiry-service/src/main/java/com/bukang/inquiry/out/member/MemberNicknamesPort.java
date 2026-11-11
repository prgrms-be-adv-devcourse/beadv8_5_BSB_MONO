package com.bukang.inquiry.out.member;

import java.util.Collection;
import java.util.Map;

/**
 * 회원 ID 여러 개로 닉네임을 받는다 (판매자·관리자 문의 목록·상세의 문의한 회원 이름)
 * 읽기만 하므로 몇 번 불러도 된다. 응답에 없는 회원은 결과에서 빠진다
 * 연결하지 못하면 InquiryExternalServiceUnavailableException(503)을 던진다. 부르는 쪽이 닉네임만 비우고 목록은 그대로 준다
 */
public interface MemberNicknamesPort {
	Map<Long, String> findNicknames(Collection<Long> memberIds);
}
