package com.bukang.order.out.crew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CrewAdapterConfigTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(CrewClientConfig.class, FakeCrewAdapter.class)
		.withPropertyValues("order.clients.timeout=2s", "order.clients.member.base-url=http://localhost:8080");

	@Test
	@DisplayName("mode가 http면 실제 회원 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void mode() {
		runner.withPropertyValues("order.clients.crew.mode=http").run(context ->
			assertThat(context).getBean(CrewPort.class).isInstanceOf(HttpCrewAdapter.class));
		runner.withPropertyValues("order.clients.crew.mode=fake").run(context ->
			assertThat(context).getBean(CrewPort.class).isInstanceOf(FakeCrewAdapter.class));
	}

	@Test
	@DisplayName("mode 설정이 빠지면 가짜 어댑터를 켜지 않아 크루 확인 빈이 없다 (아무나 크루원이 되지 않게)")
	void missingModeFailsClosed() {
		runner.run(context -> assertThat(context).doesNotHaveBean(CrewPort.class));
	}
}
