package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests;

/**
 * 판매 예정 대회를 바로 여는 API(판매자 POST /api/v1/product/races/{raceId}/open)와
 * 관리자 대회 관리 API(GET /api/v1/product/admin/races, POST …/admin/races/{raceId}/open|stop|resume)를 확인한다 (CRU-44)
 * - 바로 열면 판매 시작 일정을 지금(분 단위)으로 당기고, 판매 시작일을 오늘로, 상태를 판매 중으로 한 번에 바꾼다
 * - 관리자는 회원 서비스에 묻고, 관리자가 아니면 403, 회원 서비스가 답하지 않으면 503이다
 */
class ApiV1RaceOpenSaleTest extends ProductIntegrationTest {
	private static final String RACES_PATH = "/api/v1/product/races/";
	private static final String ADMIN_RACES_PATH = "/api/v1/product/admin/races";
	// 판매자가 아닌 관리자 회원
	private static final long ADMIN_MEMBER_ID = 1;

	@Test
	@DisplayName("판매자가 크루 예매 없는 판매 예정 대회를 열면 개인 예매 오픈이 지금으로 당겨지고 판매 중이 되며, 대기열에 새 일정을 알린다")
	void sellerOpensIndividualRace() throws Exception {
		// given: 이틀 뒤 열리는 판매 예정 대회
		RaceSaveRequestDto request = scheduledRequest().build();
		RegisteredRace scheduled = register(request);
		LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

		// when
		ResultActions opened = postAs(RACES_PATH + scheduled.raceId() + "/open", SELLER_MEMBER_ID);

		// then
		opened.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회 판매를 시작했습니다."))
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
			.andExpect(jsonPath("$.data.saleOpenDate").value(LocalDate.now().toString()));
		Race race = raceRepository.findById(scheduled.raceId()).orElseThrow();
		assertSoftly(softly -> {
			softly.assertThat(race.getSaleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);
			softly.assertThat(race.getSaleOpenDate()).isEqualTo(LocalDate.now());
			softly.assertThat(race.getIndividualOpenAt()).isBetween(before, LocalDateTime.now());
			softly.assertThat(race.getIndividualOpenAt().getSecond()).isZero();
			softly.assertThat(race.getApplicationCloseAt()).isEqualTo(request.getApplicationCloseAt());
			softly.assertThat(race.getCrewSaleStartAt()).isNull();
		});
		RaceChangedEvent sent = lastRaceChanged();
		assertSoftly(softly -> {
			softly.assertThat(sent.raceId()).isEqualTo(scheduled.raceId());
			softly.assertThat(sent.saleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);
			softly.assertThat(sent.individualOpenAt()).isEqualTo(race.getIndividualOpenAt());
		});
		// 검색 문서도 판매 중·오늘 판매 시작으로 바뀐다 (멈추기·다시 열기와 같은 동기화 이벤트)
		syncRaceDocuments();
		assertThat(raceCourseDocumentRepository.findByRaceId(String.valueOf(scheduled.raceId())))
			.extracting(RaceCourseDocument::getSaleStatus, RaceCourseDocument::getSaleOpenDate)
			.containsOnly(tuple(RaceSaleStatus.ON_SALE, LocalDate.now()));
	}

	@Test
	@DisplayName("크루 예매가 있는 대회를 열면 크루 예매 시작만 지금으로 당기고 개인 예매 오픈은 그대로 둔다")
	void openCrewRaceMovesCrewSaleStartOnly() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RaceSaveRequestDto request = scheduledRequest()
			.crewSale(now.plusDays(3), now.plusDays(5))
			.individualOpenAt(now.plusDays(6))
			.build();
		RegisteredRace scheduled = register(request);

		// when
		postAs(RACES_PATH + scheduled.raceId() + "/open", SELLER_MEMBER_ID).andExpect(status().isOk());

		// then
		Race race = raceRepository.findById(scheduled.raceId()).orElseThrow();
		assertSoftly(softly -> {
			softly.assertThat(race.getSaleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);
			softly.assertThat(race.getSaleOpenDate()).isEqualTo(LocalDate.now());
			softly.assertThat(race.getCrewSaleStartAt()).isBeforeOrEqualTo(LocalDateTime.now());
			softly.assertThat(race.getCrewSaleEndAt()).isEqualTo(request.getCrewSaleEndAt());
			softly.assertThat(race.getIndividualOpenAt()).isEqualTo(request.getIndividualOpenAt());
		});
		assertThat(lastRaceChanged().crewSaleStartAt()).isEqualTo(race.getCrewSaleStartAt());
	}

	@Test
	@DisplayName("다른 판매자의 대회는 열 수 없고(403), 이미 판매 중인 대회는 409이며 대회는 그대로다")
	void rejectNotOwnerAndOnSale() throws Exception {
		// given
		RegisteredRace scheduled = register(scheduledRequest().build());
		RegisteredRace onSale = registerOnSale(race().build());

		// when
		ResultActions notOwner = postAs(RACES_PATH + scheduled.raceId() + "/open", OTHER_SELLER_MEMBER_ID);
		ResultActions alreadyOnSale = postAs(RACES_PATH + onSale.raceId() + "/open", SELLER_MEMBER_ID);

		// then
		notOwner.andExpect(status().isForbidden());
		alreadyOnSale.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 판매 중인 대회입니다."));
		assertThat(raceRepository.findById(scheduled.raceId()).orElseThrow().getSaleStatus())
			.isEqualTo(RaceSaleStatus.SCHEDULED);
	}

	@Test
	@DisplayName("관리자가 아닌 회원은 관리자 대회 목록과 상태 변경을 쓸 수 없다 (403, 대회 판매자여도)")
	void rejectNotAdmin() throws Exception {
		// given
		RegisteredRace scheduled = register(scheduledRequest().build());

		// when
		ResultActions list = getAs(ADMIN_RACES_PATH, SELLER_MEMBER_ID);
		ResultActions open = postAs(ADMIN_RACES_PATH + "/" + scheduled.raceId() + "/open", SELLER_MEMBER_ID);

		// then
		list.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("관리자만 이용할 수 있습니다."));
		open.andExpect(status().isForbidden());
		assertThat(raceRepository.findById(scheduled.raceId()).orElseThrow().getSaleStatus())
			.isEqualTo(RaceSaleStatus.SCHEDULED);
	}

	@Test
	@DisplayName("회원 서비스가 답하지 않으면 관리자로도, 관리자가 아닌 것으로도 보지 않고 503이며 대회는 그대로다")
	void memberServiceUnavailable() throws Exception {
		// given
		RegisteredRace scheduled = register(scheduledRequest().build());
		memberAdminPort.admin(ADMIN_MEMBER_ID);
		memberAdminPort.setUnavailable(true);

		// when
		ResultActions list = getAs(ADMIN_RACES_PATH, ADMIN_MEMBER_ID);
		ResultActions open = postAs(ADMIN_RACES_PATH + "/" + scheduled.raceId() + "/open", ADMIN_MEMBER_ID);

		// then
		list.andExpect(status().isServiceUnavailable());
		open.andExpect(status().isServiceUnavailable());
		assertThat(raceRepository.findById(scheduled.raceId()).orElseThrow().getSaleStatus())
			.isEqualTo(RaceSaleStatus.SCHEDULED);
	}

	@Test
	@DisplayName("관리자 목록은 모든 판매자의 대회를 취소된 대회까지 최근 등록 순으로, 판매자 이름·환불 기준일과 함께 준다")
	void adminListsAllRaces() throws Exception {
		// given
		RegisteredRace mine = register(scheduledRequest().build());
		RegisteredRace others = register(OTHER_SELLER_MEMBER_ID, scheduledRequest().build());
		postAs(RACES_PATH + mine.raceId() + "/cancel", SELLER_MEMBER_ID).andExpect(status().isOk());
		memberAdminPort.admin(ADMIN_MEMBER_ID);

		// when
		ResultActions list = getAs(ADMIN_RACES_PATH, ADMIN_MEMBER_ID);

		// then
		list.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].raceId").value(others.raceId()))
			.andExpect(jsonPath("$.data[0].sellerName").value("(주)러닝" + OTHER_SELLER_ID))
			.andExpect(jsonPath("$.data[0].refundDeadline").exists())
			.andExpect(jsonPath("$.data[0].individualOpenAt").exists())
			.andExpect(jsonPath("$.data[0].courses[0].quota").exists())
			.andExpect(jsonPath("$.data[1].raceId").value(mine.raceId()))
			.andExpect(jsonPath("$.data[1].saleStatus").value("CANCELED"));
	}

	@Test
	@DisplayName("관리자는 판매자가 아니어도 어느 대회든 열고, 멈추고, 다시 연다")
	void adminOpensStopsResumes() throws Exception {
		// given
		RegisteredRace scheduled = register(OTHER_SELLER_MEMBER_ID, scheduledRequest().build());
		memberAdminPort.admin(ADMIN_MEMBER_ID);
		String path = ADMIN_RACES_PATH + "/" + scheduled.raceId();

		// when, then
		postAs(path + "/open", ADMIN_MEMBER_ID).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"));
		postAs(path + "/stop", ADMIN_MEMBER_ID).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("STOPPED"));
		postAs(path + "/resume", ADMIN_MEMBER_ID).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"));
		// 상태 규칙은 판매자와 같다: 판매 중인 대회를 다시 열 수 없다
		postAs(path + "/resume", ADMIN_MEMBER_ID).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("관리자 상태 변경도 없는 대회는 404다")
	void adminRaceNotFound() throws Exception {
		memberAdminPort.admin(ADMIN_MEMBER_ID);

		postAs(ADMIN_RACES_PATH + "/999999/open", ADMIN_MEMBER_ID).andExpect(status().isNotFound());
	}

	// 판매 시작일이 오늘로부터 이틀 뒤인 대회 (등록하면 판매 예정)
	private static RaceRequests.RaceBuilder scheduledRequest() {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		return race()
			.individualOpenAt(now.plusDays(2))
			.applicationCloseAt(now.plusDays(10))
			.raceDate(now.plusDays(31));
	}

	private RaceChangedEvent lastRaceChanged() {
		List<String> payloads = outboxPayloads(KafkaTopics.RACE_CHANGED);
		return jsonConverter.fromJson(payloads.getLast(), RaceChangedEvent.class);
	}
}
