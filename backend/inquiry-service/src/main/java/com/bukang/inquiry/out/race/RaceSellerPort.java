package com.bukang.inquiry.out.race;

import java.util.Optional;

/**
 * 대회의 판매자 회원 ID를 상품 서비스에 묻는다. 문의를 만들 때 한 번 묻고 문의에 저장한다
 * 대회가 없으면 빈 값이고, 연결하지 못하면 빈 값이 아니라 InquiryExternalServiceUnavailableException(503)을 던진다
 * (판매자를 모르는 문의는 답할 사람이 없으므로 만들지 않는다)
 */
public interface RaceSellerPort {
	Optional<Long> findSellerMemberId(long raceId);
}
