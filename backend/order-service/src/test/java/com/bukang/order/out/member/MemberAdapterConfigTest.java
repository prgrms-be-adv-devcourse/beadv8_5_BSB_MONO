package com.bukang.order.out.member;

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

class MemberAdapterConfigTest {
	// 실제 앱처럼 "2s" 같은 기간 값을 Duration으로 바꾸는 Spring Boot 변환기를 쓴다
	private final ApplicationContextRunner adminRunner = new ApplicationContextRunner()
		.withInitializer(context -> context.getBeanFactory()
			.setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(MemberClientConfig.class, FakeMemberAdminAdapter.class)
		.withPropertyValues("order.clients.timeout=2s", "order.clients.member.base-url=http://localhost:8080");

	private final ApplicationContextRunner nicknameRunner = new ApplicationContextRunner()
		.withUserConfiguration(FakeMemberNicknameAdapter.class, DisabledMemberNicknameAdapter.class);

	@Test
	@DisplayName("관리자 확인: mode가 http면 실제 회원 서비스 어댑터만, fake면 가짜 어댑터만 등록된다")
	void adminMode() {
		adminRunner.withPropertyValues("order.clients.member.mode=http").run(context ->
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(HttpMemberAdminAdapter.class));
		adminRunner.withPropertyValues("order.clients.member.mode=fake").run(context ->
			assertThat(context).getBean(MemberAdminPort.class).isInstanceOf(FakeMemberAdminAdapter.class));
	}

	@Test
	@DisplayName("mode 설정이 빠지면 가짜 어댑터를 켜지 않아 관리자 확인·닉네임 빈이 없다 (누구나 관리자가 되지 않게)")
	void missingModeFailsClosed() {
		adminRunner.run(context -> assertThat(context).doesNotHaveBean(MemberAdminPort.class));
		nicknameRunner.run(context -> assertThat(context).doesNotHaveBean(MemberNicknamePort.class));
	}

	@Test
	@DisplayName("닉네임: 가짜 어댑터는 시작 데이터 닉네임만 찾고, disabled면 닉네임 검색을 503으로 막는다")
	void nicknameMode() {
		nicknameRunner.withPropertyValues("order.clients.nickname.mode=fake").run(context -> {
			MemberNicknamePort port = context.getBean(MemberNicknamePort.class);
			assertThat(port).isInstanceOf(FakeMemberNicknameAdapter.class);
			assertThat(port.findMemberId("사용자1")).contains(4L);
			assertThat(port.findMemberId("없는회원")).isEmpty();
		});
		nicknameRunner.withPropertyValues("order.clients.nickname.mode=disabled").run(context -> {
			MemberNicknamePort port = context.getBean(MemberNicknamePort.class);
			assertThat(port).isInstanceOf(DisabledMemberNicknameAdapter.class);
			assertThatThrownBy(() -> port.findMemberId("사용자1"))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(DisabledMemberNicknameAdapter.MESSAGE);
		});
	}

	@Test
	@DisplayName("운영(prod) 설정은 관리자 확인을 실제 회원 서비스에 묻고, 닉네임 검색은 막아 둔다")
	void prodProfile() throws Exception {
		List<PropertySource<?>> prod = new YamlPropertySourceLoader()
			.load("prod", new ClassPathResource("application-prod.yaml"));

		assertThat(prod).anySatisfy(source -> {
			assertThat(source.getProperty("order.clients.member.mode")).isEqualTo("http");
			assertThat(source.getProperty("order.clients.member.base-url")).isEqualTo("http://bukang-member-service:8080");
			assertThat(source.getProperty("order.clients.nickname.mode")).isEqualTo("disabled");
		});
	}
}
