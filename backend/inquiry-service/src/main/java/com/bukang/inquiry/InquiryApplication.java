package com.bukang.inquiry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// common 모듈의 빈(GlobalExceptionHandler, JsonConverter, NotificationKafkaSender)도 등록되도록 com.bukang.common을 함께 스캔한다
@SpringBootApplication(scanBasePackages = {"com.bukang.inquiry", "com.bukang.common"})
@EnableJpaAuditing
public class InquiryApplication {

	public static void main(String[] args) {
		SpringApplication.run(InquiryApplication.class, args);
	}

}
