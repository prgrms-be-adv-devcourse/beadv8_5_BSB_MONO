package com.bukang.cash.out.pg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PaymentClientConfigTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(PaymentClientConfig.class, FakePaymentClient.class)
		.withPropertyValues(
			"cash.clients.pg.base-url=http://localhost",
			"cash.clients.pg.secret-key=test_sk_dummy",
			"cash.clients.pg.confirm-timeout=5s",
			"cash.clients.pg.lookup-timeout=2s"
		);

	@Test
	@DisplayName("mode가 http면 진짜 Toss를 부르는 클라이언트만 등록된다")
	void http() {
		runner.withPropertyValues("cash.clients.pg.mode=http").run(context ->
			assertThat(context).getBean(PaymentClient.class).isInstanceOf(TossPaymentClient.class));
	}

	@Test
	@DisplayName("mode가 fake이거나 값이 없으면 가짜 클라이언트만 등록된다")
	void fake() {
		runner.withPropertyValues("cash.clients.pg.mode=fake").run(context ->
			assertThat(context).getBean(PaymentClient.class).isInstanceOf(FakePaymentClient.class));
		runner.run(context ->
			assertThat(context).getBean(PaymentClient.class).isInstanceOf(FakePaymentClient.class));
	}

	@Test
	@DisplayName("운영(prod) 설정은 mode를 http로 바꾼다")
	void prodProfileUsesHttp() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source ->
			assertThat(source.getProperty("cash.clients.pg.mode")).isEqualTo("http"));
	}
}
