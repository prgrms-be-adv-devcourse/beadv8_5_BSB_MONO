package com.bukang.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class WebConfig {

	// ─────────────────────────────────────────────────────────────────────
	// [목표] JWT + Redis 기반 Stateless 인증 (의존성: jjwt, data-redis / 설정: jwt.secret)
	// - AccessToken: Authorization: Bearer {token} 헤더
	// - RefreshToken: httpOnly 쿠키 (refreshToken, deviceId)
	// - 세션/CSRF 완전 비활성화
	// [현재] JWT 필터 구현 전까지는 세션(HttpSessionSecurityContextRepository)으로 로그인 상태를 유지한다
	// ─────────────────────────────────────────────────────────────────────
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType("application/json;charset=UTF-8");
					response.getWriter().write(
						"{\"status\":401,\"message\":\"로그인이 필요합니다\",\"data\":null}"
					);
				})
			)
			.authorizeHttpRequests(auth -> auth
				// 처리되지 않은 예외는 /error로 포워드되므로, 막아 두면 원래 상태 코드(400, 500 등) 대신 401로 응답된다
				.requestMatchers("/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
				.requestMatchers("/api/v1/**").authenticated()
				.requestMatchers(HttpMethod.GET, "/**").permitAll()
				.anyRequest().authenticated()
			);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(
			AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

}
