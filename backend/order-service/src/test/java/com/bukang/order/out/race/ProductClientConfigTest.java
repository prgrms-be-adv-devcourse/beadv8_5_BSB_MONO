package com.bukang.order.out.race;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.bukang.order.out.seat.CrewSeatHoldPort;
import com.bukang.order.out.seat.FakeCrewSeatHoldAdapter;
import com.bukang.order.out.seat.FakeSeatHoldAdapter;
import com.bukang.order.out.seat.HttpCrewSeatHoldAdapter;
import com.bukang.order.out.seat.HttpSeatHoldAdapter;
import com.bukang.order.out.seat.SeatHoldPort;

class ProductClientConfigTest {
	// 실제 앱처럼 "2s" 같은 기간 값을 Duration으로 바꾸는 Spring Boot 변환기를 쓴다
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(ProductClientConfig.class, FakeRaceCourseAdapter.class, FakeSeatHoldAdapter.class,
			FakeCrewSeatHoldAdapter.class)
		.withPropertyValues("order.clients.timeout=2s", "order.clients.product.base-url=http://localhost:8085");

	@Test
	@DisplayName("mode가 http면 종목 정보·자리 확보·크루 자리 확보 모두 실제 상품 서비스 어댑터만 등록된다")
	void http() {
		runner.withPropertyValues("order.clients.product.mode=http").run(context -> {
			assertThat(context).getBean(RaceCoursePort.class).isInstanceOf(HttpRaceCourseAdapter.class);
			assertThat(context).getBean(SeatHoldPort.class).isInstanceOf(HttpSeatHoldAdapter.class);
			assertThat(context).getBean(CrewSeatHoldPort.class).isInstanceOf(HttpCrewSeatHoldAdapter.class);
		});
	}

	@Test
	@DisplayName("운영(prod) 설정은 실제 상품 서비스를 컨테이너 이름으로 부른다")
	void prodProfileUsesHttp() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source -> {
			assertThat(source.getProperty("order.clients.product.mode")).isEqualTo("http");
			assertThat(source.getProperty("order.clients.product.base-url"))
				.isEqualTo("http://bukang-product-service:8085");
		});
	}

	@Test
	@DisplayName("mode가 fake이거나 값이 없으면 가짜 어댑터만 등록된다")
	void fake() {
		runner.withPropertyValues("order.clients.product.mode=fake").run(context -> {
			assertThat(context).getBean(RaceCoursePort.class).isInstanceOf(FakeRaceCourseAdapter.class);
			assertThat(context).getBean(SeatHoldPort.class).isInstanceOf(FakeSeatHoldAdapter.class);
			assertThat(context).getBean(CrewSeatHoldPort.class).isInstanceOf(FakeCrewSeatHoldAdapter.class);
		});
		runner.run(context -> {
			assertThat(context).getBean(RaceCoursePort.class).isInstanceOf(FakeRaceCourseAdapter.class);
			assertThat(context).getBean(SeatHoldPort.class).isInstanceOf(FakeSeatHoldAdapter.class);
		});
	}
}
