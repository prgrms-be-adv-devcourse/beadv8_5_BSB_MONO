package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.product.domain.CourseType;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * member 서비스가 부르는 종목 기록증 규칙 조회 내부 API를 확인한다 (BUK-270).
 * 판매자 Fixture는 판매자 ID(7001)와 회원 ID(9001)가 달라, 판매자 ID를 잘못 돌려주면 실패한다
 */
class InternalV1RecordCertRuleTest extends ProductIntegrationTest {
	@Test
	@DisplayName("규칙(2025-01-01부터 · 2시간 이내 · 하프와 풀)을 넣고 등록한 종목을 조회하면 규칙과 판매자 회원 ID 9001이 온다")
	void findRule() throws Exception {
		RegisteredRace registered = registerOnSale(race()
			.course(course().recordCertRule(LocalDate.of(2025, 1, 1), 7200, CourseType.HALF, CourseType.FULL))
			.build());

		findRule(registered.courseId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("종목 기록증 규칙을 조회했습니다."))
			.andExpect(jsonPath("$.data.sellerMemberId").value(SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.rule.recognizedSince").value("2025-01-01"))
			.andExpect(jsonPath("$.data.rule.maxNetSeconds").value(7200))
			.andExpect(jsonPath("$.data.rule.allowedCourseTypes", containsInAnyOrder("HALF", "FULL")));
	}

	@Test
	@DisplayName("규칙 없이 등록한 종목을 조회하면 규칙은 비어 있고 판매자 회원 ID 9001은 온다")
	void findNoRule() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());

		findRule(registered.courseId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sellerMemberId").value(SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.rule").isEmpty());
	}

	@Test
	@DisplayName("없는 종목을 조회하면 404이다")
	void notFound() throws Exception {
		findRule(999_999)
			.andExpect(status().isNotFound());
	}

	private ResultActions findRule(long raceCourseId) throws Exception {
		return getAs("/internal/v1/product/courses/" + raceCourseId + "/record-cert-rule", null);
	}
}
