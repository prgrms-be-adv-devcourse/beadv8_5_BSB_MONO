package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.ProductMember;
import com.bukang.product.domain.ProductSeller;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 회원 서비스가 판매자 탈퇴 전에 부르는 내부 API(InternalV1SellerRaceController)를 확인한다 (CRU-34).
 * 판매 예정·판매 중·판매 중지 대회가 있으면 true, 판매 종료·대회 취소만 있거나 대회가 없으면 false,
 * 판매자로 등록되지 않은 회원은 탈퇴를 막도록 true와 판매자 아님 문구다.
 * 판매자 Fixture는 판매자 ID(SELLER_ID 7001)와 회원 ID(SELLER_MEMBER_ID 9001)가 달라, 두 ID를 헷갈리면 실패한다
 */
class InternalV1SellerRaceTest extends ProductIntegrationTest {
	private static final String SELLERS_PATH = "/internal/v1/product/sellers/";
	private static final String RACES_PATH = "/api/v1/product/races/";
	private static final String CHECKED_MESSAGE = "판매가 끝나지 않은 대회가 있는지 조회했습니다.";
	private static final String NOT_SELLER_MESSAGE = "판매자로 등록된 회원이 아닙니다.";

	@Test
	@DisplayName("판매 예정 대회만 있는 판매자(회원 9001)는 true다")
	void scheduledRace() throws Exception {
		// given
		RegisteredRace scheduled = register(race().individualOpenAt(LocalDateTime.now().plusDays(2)).build());
		assertSaleStatus(scheduled, RaceSaleStatus.SCHEDULED);

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, true);
	}

	@Test
	@DisplayName("판매 중 대회가 있는 판매자는 true다")
	void onSaleRace() throws Exception {
		// given
		RegisteredRace onSale = registerOnSale(race().build());
		assertSaleStatus(onSale, RaceSaleStatus.ON_SALE);

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, true);
	}

	@Test
	@DisplayName("판매 중지 대회가 있는 판매자는 다시 열 수 있으므로 true다")
	void stoppedRace() throws Exception {
		// given
		RegisteredRace stopped = registerOnSale(race().build());
		postJson(RACES_PATH + stopped.raceId() + "/stop", SELLER_MEMBER_ID, null).andExpect(status().isOk());
		assertSaleStatus(stopped, RaceSaleStatus.STOPPED);

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, true);
	}

	@Test
	@DisplayName("상태 배치가 판매 종료로 바꾼 대회만 있는 판매자는 false다")
	void closedRaceOnly() throws Exception {
		// given
		RegisteredRace closed = registerClosed();

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		assertSaleStatus(closed, RaceSaleStatus.CLOSED);
		expectChecked(found, false);
	}

	@Test
	@DisplayName("대회 취소한 대회만 있는 판매자는 false다")
	void canceledRaceOnly() throws Exception {
		// given
		RegisteredRace canceled = registerCanceled();

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		assertSaleStatus(canceled, RaceSaleStatus.CANCELED);
		expectChecked(found, false);
	}

	@Test
	@DisplayName("판매 종료·대회 취소 대회와 판매 중 대회가 함께 있으면 true다")
	void finishedAndOnSaleRaces() throws Exception {
		// given
		registerClosed();
		registerCanceled();
		registerOnSale(race().title("판매 중인 대회").build());

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, true);
	}

	@Test
	@DisplayName("대회를 하나도 등록하지 않은 판매자는 false다")
	void sellerWithoutRace() throws Exception {
		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, false);
	}

	@Test
	@DisplayName("다른 판매자(회원 9002)의 판매 중 대회는 세지 않아 판매자(회원 9001)는 false다")
	void otherSellersRaceOnly() throws Exception {
		// given
		register(OTHER_SELLER_MEMBER_ID, race().individualOpenAt(LocalDateTime.now().plusDays(2)).build());

		// when
		ResultActions found = findOnSaleRace(SELLER_MEMBER_ID);

		// then
		expectChecked(found, false);
	}

	@Test
	@DisplayName("판매자 복제본이 없는 회원(9999)은 탈퇴를 막도록 true와 판매자 아님 문구다")
	void notSeller() throws Exception {
		// when
		ResultActions found = findOnSaleRace(NOT_SELLER_MEMBER_ID);

		// then
		expectNotSeller(found);
	}

	@Test
	@DisplayName("승인 대기 판매자 행만 있는 회원(9003)도 판매자로 보고, 대회가 없으니 false다")
	void pendingSellerWithoutRace() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		ProductMember member = productMemberRepository.save(new ProductMember(9003, now, now, "러너9003"));
		productSellerRepository.save(new ProductSeller(7003, member, SellerType.BUSINESS, "(주)러닝7003",
			"123-45-67890", "김대표", SellerStatus.PENDING, now, now));

		// when
		ResultActions found = findOnSaleRace(9003);

		// then
		expectChecked(found, false);
	}

	@Test
	@DisplayName("회원 ID 대신 판매자 ID(7001)로 부르면 그런 회원의 판매자 행이 없어 판매자 아님 문구다")
	void sellerIdInsteadOfMemberId() throws Exception {
		// given: 회원 9001(판매자 7001)에게 판매 중 대회가 있다
		registerOnSale(race().build());

		// when
		ResultActions found = findOnSaleRace(SELLER_ID);

		// then
		expectNotSeller(found);
	}

	@Test
	@DisplayName("회원 ID가 숫자가 아니면 400 '요청 값의 형식이 올바르지 않습니다.'다")
	void invalidMemberId() throws Exception {
		// when
		ResultActions found = getAs(SELLERS_PATH + "abc/races/on-sale", null);

		// then
		found.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	private ResultActions findOnSaleRace(long memberId) throws Exception {
		return getAs(SELLERS_PATH + memberId + "/races/on-sale", null);
	}

	private void expectChecked(ResultActions found, boolean hasOnSaleRace) throws Exception {
		found.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value(CHECKED_MESSAGE))
			.andExpect(jsonPath("$.data.hasOnSaleRace").value(hasOnSaleRace));
	}

	private void expectNotSeller(ResultActions found) throws Exception {
		found.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value(NOT_SELLER_MESSAGE))
			.andExpect(jsonPath("$.data.hasOnSaleRace").value(true));
	}

	// 대회일이 지난 대회를 판매 중으로 등록한 뒤 상태 배치를 돌려 판매 종료로 만든다
	private RegisteredRace registerClosed() throws Exception {
		RegisteredRace closed = registerOnSale(pastRace());
		productFacade.refreshSaleStatuses();
		return closed;
	}

	// 판매 예정 대회를 등록한 뒤 판매자가 취소한다
	private RegisteredRace registerCanceled() throws Exception {
		RegisteredRace canceled = register(race().individualOpenAt(LocalDateTime.now().plusDays(2)).build());
		postAs(RACES_PATH + canceled.raceId() + "/cancel", SELLER_MEMBER_ID).andExpect(status().isOk());
		return canceled;
	}

	// 대회일이 이틀 전인 대회 (개인 예매 오픈 5일 전, 접수 마감 3일 전). ApiV1RaceCancelTest와 같은 요청
	private RaceSaveRequestDto pastRace() {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		return race()
			.title("끝난 대회")
			.raceDate(now.minusDays(2))
			.individualOpenAt(now.minusDays(5))
			.applicationCloseAt(now.minusDays(3))
			.build();
	}

	private void assertSaleStatus(RegisteredRace registered, RaceSaleStatus expected) {
		assertThat(raceRepository.findById(registered.raceId()).orElseThrow().getSaleStatus()).isEqualTo(expected);
	}
}
