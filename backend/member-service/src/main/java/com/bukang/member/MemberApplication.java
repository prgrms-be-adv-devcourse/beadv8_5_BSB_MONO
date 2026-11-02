package com.bukang.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

// common 모듈의 빈(GlobalExceptionHandler, JsonConverter)도 등록되도록 com.bukang.common을 함께 스캔한다
// 엔티티와 Repository는 이 클래스의 패키지(com.bukang.member) 기준으로 스캔된다
@SpringBootApplication(scanBasePackages = {"com.bukang.member", "com.bukang.common"})
@EnableJpaAuditing
@EnableScheduling // 탈퇴 후 재가입 제한 해시 정리(RejoinBlockCleanupScheduler, test 프로파일 제외)
// @ConfigurationProperties 설정 클래스(config/auth/RefreshTokenProperties 등)를 빈으로 등록한다
@ConfigurationPropertiesScan
public class MemberApplication {

	public static void main(String[] args) {
		SpringApplication.run(MemberApplication.class, args);
	}

	@Bean
	public JPAQueryFactory queryFactory(EntityManager em) {
		return new JPAQueryFactory(em);
	}

}
