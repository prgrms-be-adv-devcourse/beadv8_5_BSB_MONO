package com.bukang.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.config.EnableElasticsearchAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

// common 모듈의 빈(GlobalExceptionHandler, JsonConverter)도 등록되도록 com.bukang.common을 함께 스캔한다
@SpringBootApplication(scanBasePackages = {"com.bukang.product", "com.bukang.common"})
@EnableJpaAuditing
// ES 문서의 createdAt·lastModifiedAt (domain/document/BaseDocument)
@EnableElasticsearchAuditing
// 대회 판매 상태 배치(RaceSaleStatusScheduler), 아웃박스 릴레이(ProductOutboxRelayScheduler). 둘 다 test 프로파일 제외
@EnableScheduling
public class ProductApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductApplication.class, args);
	}

	@Bean
	public JPAQueryFactory queryFactory(EntityManager em) {
		return new JPAQueryFactory(em);
	}

}
