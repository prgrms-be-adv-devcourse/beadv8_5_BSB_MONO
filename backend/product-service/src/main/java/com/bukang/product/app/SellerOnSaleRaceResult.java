package com.bukang.product.app;

import com.bukang.product.dto.SellerOnSaleRaceDto;

/**
 * 판매자의 끝나지 않은 대회 확인 결과. 판매자로 등록된 회원인지에 따라 결과를 나눈다
 * 컨트롤러가 결과를 보고 응답 문구를 고른다 (응답 코드는 둘 다 200이다)
 */
public record SellerOnSaleRaceResult(Outcome outcome, SellerOnSaleRaceDto race) {
	static SellerOnSaleRaceResult notSeller() {
		return new SellerOnSaleRaceResult(Outcome.NOT_SELLER, new SellerOnSaleRaceDto(true));
	}

	static SellerOnSaleRaceResult checked(boolean hasOnSaleRace) {
		return new SellerOnSaleRaceResult(Outcome.CHECKED, new SellerOnSaleRaceDto(hasOnSaleRace));
	}

	public enum Outcome {
		// 판매자라 대회 상태를 확인했다
		CHECKED,
		// 판매자 복제본이 없는 회원이라 탈퇴를 막도록 true로 답한다
		NOT_SELLER
	}
}
