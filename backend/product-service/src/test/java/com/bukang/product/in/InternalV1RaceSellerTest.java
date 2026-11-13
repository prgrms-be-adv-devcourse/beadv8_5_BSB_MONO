package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.product.support.ProductIntegrationTest;

/**
 * 문의 서비스가 부르는 대회 판매자 조회 내부 API(InternalV1RaceSellerController)를 확인한다 (BUK-159).
 * 판매자 Fixture는 판매자 ID(SELLER_ID 7001)와 회원 ID(SELLER_MEMBER_ID 9001)가 달라,
 * 판매자 ID를 잘못 돌려주면 이 테스트가 실패한다
 */
class InternalV1RaceSellerTest extends ProductIntegrationTest {
	@Test
	@DisplayName("판매자(판매자 ID 7001, 회원 ID 9001)가 등록한 대회를 조회하면 200이고 sellerMemberId로 회원 ID 9001이 온다")
	void findSellerMember() throws Exception {
		// given
		RegisteredRace registered = registerOnSale(race().build());

		// when
		ResultActions found = getAs("/internal/v1/product/races/" + registered.raceId() + "/seller", null);

		// then
		found.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회 판매자를 조회했습니다."))
			.andExpect(jsonPath("$.data.sellerMemberId").value(SELLER_MEMBER_ID));
	}
}
