package com.bukang.payout.out;

import java.util.Optional;

import com.bukang.common.shared.seller.domain.SellerStatus;

/**
 * 회원 ID로 그 회원의 판매자 신청(판매자 ID · 상태)을 찾는 계약 (포트)
 * 게이트웨이는 회원 ID(X-Member-Id)만 넘겨 주는데, 정산 내역은 판매자 ID(MEMBER_SELLER.seller_id)로 묶여 있어 둘을 이어야 한다
 *
 * 지금은 회원 서비스 내부 API를 HTTP로 묻는다 (MemberSellerLookupAdapter, 임시 방식)
 * 나중에 판매자 변경 이벤트(queuing.seller.changed)를 받아 정산에 판매자 복제본을 두는 방식으로 바꾸면 어댑터만 바꾼다
 */
@FunctionalInterface
public interface SellerLookupPort {
	/**
	 * @return 회원의 가장 최근 판매자 신청. 신청이 없으면 두 칸이 null인 값, 존재하지 않거나 탈퇴한 회원이면 빈 값
	 * @throws com.bukang.payout.domain.exception.PayoutSellerLookupUnavailableException 판매자 정보를 확인하지 못함 (503).
	 *         빈 값이나 '신청 없음'으로 바꿔 돌려주지 않는다 (판매자가 "정산이 없다"로 잘못 읽는다)
	 */
	Optional<SellerOfMember> findSellerOf(long memberId);

	/**
	 * @param sellerId 판매자 신청서 ID (MEMBER_SELLER.seller_id). 회원 ID와 다른 값이다
	 * @param status   그 신청의 상태
	 */
	record SellerOfMember(Long sellerId, SellerStatus status) {
		public boolean isApproved() {
			return status == SellerStatus.APPROVED && sellerId != null;
		}
	}
}
