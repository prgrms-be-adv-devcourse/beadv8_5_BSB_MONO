package com.bukang.common.shared.member.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @LoginMemberId 리졸버를 등록한다
 * 모든 서비스가 scanBasePackages에 com.bukang.common을 넣으므로 서비스마다 따로 설정하지 않아도 적용된다
 */
@Configuration
public class LoginMemberIdConfig implements WebMvcConfigurer {
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new LoginMemberIdArgumentResolver());
	}
}
