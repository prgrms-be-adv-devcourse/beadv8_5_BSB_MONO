package com.bukang.cash.out.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class MemberClientConfigTest {
	// 실제 앱처럼 "2s" 같은 기간 값을 Duration으로 바꾸는 Spring Boot 변환기를 쓴다
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(MemberClientConfig.class, FakeMemberAdminAdapter.class)
		.withPropertyValues("cash.clients.member.timeout=2s", "cash.clients.member.base-url=http://localhost:8080");

	@Test
	@DisplayName("mode가 http면 실제 회원 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void mode() {
		runner.withPropertyValues("cash.clients.member.mode=http").run(context ->
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(HttpMemberAdminAdapter.class));
		runner.withPropertyValues("cash.clients.member.mode=fake").run(context ->
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(FakeMemberAdminAdapter.class));
	}

	@Test
	@DisplayName("mode 설정이 빠지면 가짜 어댑터를 켜지 않아 관리자 확인 빈이 없다 (누구나 관리자가 되지 않게)")
	void missingModeFailsClosed() {
		runner.run(context -> assertThat(context).doesNotHaveBean(MemberAdminPort.class));
	}

	@Test
	@DisplayName("운영(prod) 설정은 관리자 확인을 실제 회원 서비스에 묻는다")
	void prodProfile() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source -> {
			assertThat(source.getProperty("cash.clients.member.mode")).isEqualTo("http");
			assertThat(source.getProperty("cash.clients.member.base-url")).isEqualTo("http://bukang-member-service:8080");
		});
	}
}
