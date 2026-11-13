package com.bukang.board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// common 모듈의 빈(GlobalExceptionHandler, JsonConverter, 회원 ID 헤더 리졸버)도 등록되도록 com.bukang.common을 함께 스캔한다
@SpringBootApplication(scanBasePackages = {"com.bukang.board", "com.bukang.common"})
@EnableJpaAuditing
@EnableScheduling // 색인 아웃박스를 1초마다 Kafka로 보낸다 (BUK-179)
public class BoardApplication {

	public static void main(String[] args) {
		SpringApplication.run(BoardApplication.class, args);
	}

}
