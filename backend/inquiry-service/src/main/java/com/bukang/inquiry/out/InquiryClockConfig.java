package com.bukang.inquiry.out;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 시각은 이 Clock으로만 읽는다. 테스트가 시계를 바꿔 끼울 수 있다
@Configuration
public class InquiryClockConfig {
	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
