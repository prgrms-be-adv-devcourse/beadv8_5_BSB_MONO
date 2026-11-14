package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.bukang.inquiry.out.member.FakeMemberAdminAdapter;
import com.bukang.inquiry.out.member.FakeMemberNicknamesAdapter;
import com.bukang.inquiry.out.member.FakeMemberSellerAdapter;
import com.bukang.inquiry.out.member.HttpMemberAdminAdapter;
import com.bukang.inquiry.out.member.HttpMemberNicknamesAdapter;
import com.bukang.inquiry.out.member.HttpMemberSellerAdapter;
import com.bukang.inquiry.out.member.MemberAdminPort;
import com.bukang.inquiry.out.member.MemberClientConfig;
import com.bukang.inquiry.out.member.MemberNicknamesPort;
import com.bukang.inquiry.out.member.MemberSellerPort;
import com.bukang.inquiry.out.order.FakeOrderLookupAdapter;
import com.bukang.inquiry.out.order.HttpOrderLookupAdapter;
import com.bukang.inquiry.out.order.OrderClientConfig;
import com.bukang.inquiry.out.order.OrderLookupPort;
import com.bukang.inquiry.out.race.FakeRaceSellerAdapter;
import com.bukang.inquiry.out.race.HttpRaceSellerAdapter;
import com.bukang.inquiry.out.race.ProductClientConfig;
import com.bukang.inquiry.out.race.RaceSellerPort;

/**
 * 회원·상품 서비스 연결이 설정(mode)으로만 켜지는지 확인한다.
 * mode가 빠지면 가짜가 켜지지 않아, 누구나 관리자가 되는 대신 앱이 뜨지 않는다
 */
class InquiryAdapterConfigTest {
	// 실제 앱처럼 "2s" 같은 기간 값을 Duration으로 바꾸는 Spring Boot 변환기를 쓴다
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(MemberClientConfig.class, ProductClientConfig.class, OrderClientConfig.class,
			FakeMemberAdminAdapter.class, FakeMemberSellerAdapter.class, FakeMemberNicknamesAdapter.class,
			FakeRaceSellerAdapter.class, FakeOrderLookupAdapter.class)
		.withPropertyValues("inquiry.clients.timeout=2s", "inquiry.clients.member.base-url=http://localhost:8080",
			"inquiry.clients.product.base-url=http://localhost:8085",
			"inquiry.clients.order.base-url=http://localhost:8084");

	@Test
	@DisplayName("관리자 확인: mode가 http면 회원 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void memberMode() {
		runner.withPropertyValues("inquiry.clients.member.mode=http").run(context -> {
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(HttpMemberAdminAdapter.class);
			assertThat(context).getBean(MemberSellerPort.class).isInstanceOf(HttpMemberSellerAdapter.class);
			assertThat(context).getBean(MemberNicknamesPort.class).isInstanceOf(HttpMemberNicknamesAdapter.class);
		});
		runner.withPropertyValues("inquiry.clients.member.mode=fake").run(context -> {
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(FakeMemberAdminAdapter.class);
			assertThat(context).getBean(MemberSellerPort.class).isInstanceOf(FakeMemberSellerAdapter.class);
			assertThat(context).getBean(MemberNicknamesPort.class).isInstanceOf(FakeMemberNicknamesAdapter.class);
		});
	}

	@Test
	@DisplayName("판매자 조회: mode가 http면 상품 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void productMode() {
		runner.withPropertyValues("inquiry.clients.product.mode=http").run(context ->
			assertThat(context).getBean(RaceSellerPort.class).isInstanceOf(HttpRaceSellerAdapter.class));
		runner.withPropertyValues("inquiry.clients.product.mode=fake").run(context ->
			assertThat(context).getBean(RaceSellerPort.class).isInstanceOf(FakeRaceSellerAdapter.class));
	}

	@Test
	@DisplayName("주문 조회: mode가 http면 주문 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void orderMode() {
		runner.withPropertyValues("inquiry.clients.order.mode=http").run(context ->
			assertThat(context).getBean(OrderLookupPort.class).isInstanceOf(HttpOrderLookupAdapter.class));
		runner.withPropertyValues("inquiry.clients.order.mode=fake").run(context ->
			assertThat(context).getBean(OrderLookupPort.class).isInstanceOf(FakeOrderLookupAdapter.class));
	}

	@Test
	@DisplayName("mode 설정이 빠지면 관리자 확인·판매자 조회 빈이 없다 (가짜로 열리지 않는다)")
	void missingModeFailsClosed() {
		runner.run(context -> assertThat(context)
			.doesNotHaveBean(MemberAdminPort.class)
			.doesNotHaveBean(MemberSellerPort.class)
			.doesNotHaveBean(MemberNicknamesPort.class)
			.doesNotHaveBean(RaceSellerPort.class)
			.doesNotHaveBean(OrderLookupPort.class));
	}

	@Test
	@DisplayName("개발(dev) 설정은 회원·상품·주문 모두 fake, 운영(prod) 설정은 모두 http이고 컨테이너 이름으로 부른다")
	void profiles() throws Exception {
		List<PropertySource<?>> dev = new YamlPropertySourceLoader()
			.load("dev", new ClassPathResource("application-dev.yaml"));
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(dev).anySatisfy(source -> {
			assertThat(source.getProperty("inquiry.clients.member.mode")).isEqualTo("fake");
			assertThat(source.getProperty("inquiry.clients.product.mode")).isEqualTo("fake");
			assertThat(source.getProperty("inquiry.clients.order.mode")).isEqualTo("fake");
		});
		assertThat(prod).anySatisfy(source -> {
			assertThat(source.getProperty("inquiry.clients.member.mode")).isEqualTo("http");
			assertThat(source.getProperty("inquiry.clients.member.base-url"))
				.isEqualTo("http://bukang-member-service:8080");
			assertThat(source.getProperty("inquiry.clients.product.mode")).isEqualTo("http");
			assertThat(source.getProperty("inquiry.clients.product.base-url"))
				.isEqualTo("http://bukang-product-service:8085");
			assertThat(source.getProperty("inquiry.clients.order.mode")).isEqualTo("http");
			assertThat(source.getProperty("inquiry.clients.order.base-url"))
				.isEqualTo("http://bukang-order-service:8084");
		});
	}
}
