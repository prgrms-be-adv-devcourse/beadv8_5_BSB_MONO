package com.bukang.order.out.race;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 상품 서비스를 띄우지 않고 개발할 때, 어떤 종목 ID든 지금 접수 중인 종목으로 돌려준다
 * - 대회 ID는 종목 ID와 같게 둔다 (종목 하나 = 대회 하나)
 * - 대회는 60일 뒤, 접수는 하루 전에 열려 30일 뒤에 마감, 만 14세 이상
 * - 판매자 ID 1, 참가비 50,000원(키트 포함), 환불 기한은 40일 뒤, 티셔츠는 XS~XXXL 모두
 * order.clients.product.mode=fake(기본값)일 때만 쓰고, http면 HttpRaceCourseAdapter가 부른다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeRaceCourseAdapter implements RaceCoursePort {
	// 상품 서비스 TshirtSize가 가진 사이즈 전부
	private static final List<String> TSHIRT_SIZES = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeRaceCourseAdapter() {
		log.warn("종목 정보는 가짜 응답(FakeRaceCourseAdapter)을 씁니다. 실제 상품 서비스는 order.clients.product.mode=http");
	}

	@Override
	public Optional<RaceCourseInfo> findCourse(long raceCourseId) {
		if (raceCourseId <= 0) {
			return Optional.empty();
		}
		log.debug("가짜 종목 정보를 돌려줍니다. raceCourseId={}", raceCourseId);
		LocalDateTime now = LocalDateTime.now();
		return Optional.of(new RaceCourseInfo(
			raceCourseId,
			raceCourseId,
			now.plusDays(60),
			now.minusDays(1),
			now.plusDays(30),
			14,
			null,
			1,
			"가짜 대회 %d 종목".formatted(raceCourseId),
			50_000,
			true,
			now.plusDays(40).toLocalDate(),
			null,
			false,
			TSHIRT_SIZES
		));
	}
}
