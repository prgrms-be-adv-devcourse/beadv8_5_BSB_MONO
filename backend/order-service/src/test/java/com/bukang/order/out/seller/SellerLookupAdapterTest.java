package com.bukang.order.out.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class SellerLookupAdapterTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(FakeSellerLookupAdapter.class, DisabledSellerLookupAdapter.class,
			SellerClientConfig.class)
		.withPropertyValues("order.clients.timeout=2s", "order.clients.member.base-url=http://localhost:8080");

	@Test
	@DisplayName("mode가 fake이거나 값이 없으면 가짜 어댑터가 회원 ID를 판매자 ID로 답한다")
	void fake() {
		runner.withPropertyValues("order.clients.seller.mode=fake").run(context ->
			assertThat(context).getBean(SellerLookupPort.class).isInstanceOf(FakeSellerLookupAdapter.class));
		runner.run(context -> {
			SellerLookupPort port = context.getBean(SellerLookupPort.class);
			assertThat(port).isInstanceOf(FakeSellerLookupAdapter.class);
			assertThat(port.findApprovedSellerId(7)).contains(7L);
		});
	}

	@Test
	@DisplayName("mode가 disabled면 판매자 확인을 503으로 막는다 (가짜로 누구나 판매자가 되지 않게)")
	void disabled() {
		runner.withPropertyValues("order.clients.seller.mode=disabled").run(context -> {
			SellerLookupPort port = context.getBean(SellerLookupPort.class);
			assertThat(port).isInstanceOf(DisabledSellerLookupAdapter.class);
			assertThatThrownBy(() -> port.findApprovedSellerId(7))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(DisabledSellerLookupAdapter.MESSAGE);
		});
	}

	@Test
	@DisplayName("mode가 http면 회원 서비스를 부르는 실제 어댑터만 등록된다")
	void http() {
		runner.withPropertyValues("order.clients.seller.mode=http").run(context ->
			assertThat(context).getBean(SellerLookupPort.class).isInstanceOf(HttpSellerLookupAdapter.class));
	}

	@Test
	@DisplayName("운영(prod) 설정은 판매자 확인을 실제 회원 서비스에 묻는다 (BUK-237)")
	void prodProfileUsesHttp() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source -> {
			assertThat(source.getProperty("order.clients.seller.mode")).isEqualTo("http");
			assertThat(source.getProperty("order.clients.member.base-url"))
				.isEqualTo("http://bukang-member-service:8080");
		});
	}
}
