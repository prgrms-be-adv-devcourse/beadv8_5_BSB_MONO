package com.bukang.board.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각은 이 Clock으로만 읽는다. 테스트가 고정 시각으로 바꿔 끼울 수 있다
 */
@Configuration
public class BoardClockConfig {
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
