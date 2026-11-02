package com.bukang.order.out.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class SellerLookupAdapterTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeSellerLookupAdapter.class, DisabledSellerLookupAdapter.class);

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
	@DisplayName("운영(prod) 설정은 판매자 확인을 막아 둔다 (실제 어댑터를 붙이기 전까지)")
	void prodProfileDisablesSeller() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source ->
			assertThat(source.getProperty("order.clients.seller.mode")).isEqualTo("disabled"));
	}
}
