package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.HomeBanner;
import com.bukang.product.out.HomeBannerRepository;
import com.bukang.product.support.ProductIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 홈 배너 저장·관리자 조회·공개 조회 (CRU-147)
 */
class ApiV1HomeBannerTest extends ProductIntegrationTest {
	private static final String ADMIN_PATH = "/api/v1/product/admin/banners";
	private static final String PUBLIC_PATH = "/api/v1/product/banners";
	private static final long ADMIN_MEMBER_ID = 1;

	@Autowired
	private HomeBannerRepository homeBannerRepository;
	@PersistenceContext
	private EntityManager entityManager;

	@BeforeEach
	void setUpAdmin() {
		memberAdminPort.admin(ADMIN_MEMBER_ID);
	}

	// 배너가 대회를 참조하므로 공통 정리(대회 지우기)보다 먼저 지운다 (하위 클래스의 @AfterEach가 먼저 돈다)
	@AfterEach
	void cleanUpBanners() {
		homeBannerRepository.deleteAll();
	}

	@Test
	@DisplayName("배너를 다시 저장하면 앞 목록이 사라지고 새로 보낸 순서대로 조회된다 (앞 목록의 대회를 다시 넣어도 된다)")
	void replaceBanners() throws Exception {
		// given: 대회 셋 중 [셋째, 첫째]를 배너로 저장해 둔다
		long first = registerOnSale(race().title("첫째 대회").build()).raceId();
		long second = registerOnSale(race().title("둘째 대회").build()).raceId();
		long third = registerOnSale(race().title("셋째 대회").build()).raceId();
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(third, first))).andExpect(status().isOk());

		// when
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(first, second))).andExpect(status().isOk());

		// then
		getAs(PUBLIC_PATH, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].raceId").value(contains((int)first, (int)second)));
	}

	@Test
	@DisplayName("판매 중지·접수 마감 대회는 홈 배너에서 빠지고, 관리자 목록에는 안 보이는 이유와 함께 남는다")
	void hiddenRacesStayInAdminList() throws Exception {
		// given: 보이는 대회, 판매 중지 대회, 접수 마감이 지난 대회를 배너로 저장
		long visible = registerOnSale(race().title("보이는 대회").build()).raceId();
		long stopped = registerOnSale(race().title("판매 중지 대회").build()).raceId();
		long applicationClosed = registerOnSale(race().title("접수 마감 대회").build()).raceId();
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(visible, stopped, applicationClosed)))
			.andExpect(status().isOk());
		updateSaleStatus(stopped, RaceSaleStatus.STOPPED);
		updateApplicationCloseAt(applicationClosed, LocalDateTime.now().minusHours(1));

		// when, then
		getAs(PUBLIC_PATH, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].raceId").value(contains((int)visible)));
		getAs(ADMIN_PATH, ADMIN_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].raceId").value(contains((int)visible, (int)stopped, (int)applicationClosed)))
			.andExpect(jsonPath("$.data[0].hiddenReason").value(nullValue()))
			.andExpect(jsonPath("$.data[1].hiddenReason").value("STOPPED"))
			.andExpect(jsonPath("$.data[2].hiddenReason").value("APPLICATION_CLOSED"));
	}

	@Test
	@DisplayName("6개를 보내거나 같은 대회를 두 번 보내거나 취소된 대회를 보내면 막히고, 저장해 둔 배너는 그대로다")
	void rejectInvalidBanners() throws Exception {
		// given: 대회 하나를 배너로 저장해 두고, 취소된 대회를 하나 만든다
		long saved = registerOnSale(race().title("저장해 둔 대회").build()).raceId();
		long canceled = registerOnSale(race().title("취소된 대회").build()).raceId();
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(saved))).andExpect(status().isOk());
		updateSaleStatus(canceled, RaceSaleStatus.CANCELED);

		// when, then
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(1L, 2L, 3L, 4L, 5L, 6L)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("홈 배너는 최대 5개까지 고를 수 있습니다."));
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(saved, saved)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("같은 대회를 홈 배너에 두 번 넣을 수 없습니다."));
		putJson(ADMIN_PATH, ADMIN_MEMBER_ID, Map.of("raceIds", List.of(canceled)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("취소된 대회는 배너로 고를 수 없습니다."));
		assertThat(homeBannerRepository.findAllByOrderByDisplayOrderAsc())
			.extracting(HomeBanner::getRaceId)
			.containsExactly(saved);
	}

	// 판매 상태를 API를 거치지 않고 바꾼다
	private void updateSaleStatus(long raceId, RaceSaleStatus saleStatus) {
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("update Race r set r.saleStatus = :saleStatus where r.id = :id")
			.setParameter("saleStatus", saleStatus)
			.setParameter("id", raceId)
			.executeUpdate());
	}

	// 접수 마감 일시를 API를 거치지 않고 바꾼다
	private void updateApplicationCloseAt(long raceId, LocalDateTime applicationCloseAt) {
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("update Race r set r.applicationCloseAt = :closeAt where r.id = :id")
			.setParameter("closeAt", applicationCloseAt)
			.setParameter("id", raceId)
			.executeUpdate());
	}
}
