package com.bukang.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * 대회 판매 설정(RaceSaleProperties)을 빈으로 등록한다
 * 테스트용 당일 판매가 켜진 채로 뜨면 경고를 남긴다. 테스트가 끝나면 꺼야 하는 설정이라 로그로 알아보게 한다
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RaceSaleProperties.class)
public class RaceSaleConfig {
	public RaceSaleConfig(RaceSaleProperties properties) {
		if (properties.sameDaySaleEnabled()) {
			log.warn("테스트용 당일 판매가 켜져 있습니다. 판매 시작일이 오늘인 대회도 등록되고 바로 판매 중이 됩니다. "
				+ "테스트가 끝나면 PRODUCT_RACE_SAME_DAY_SALE_ENABLED를 빼고 다시 띄워 주세요.");
		}
	}
}
