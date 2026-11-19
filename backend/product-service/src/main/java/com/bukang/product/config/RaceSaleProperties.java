package com.bukang.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대회 판매 설정 (product.race.*)
 *
 * @param sameDaySaleEnabled 테스트용 당일 판매. 켜면 등록할 때 판매 시작일을 오늘부터 받고,
 *                           판매 시작일이 오늘인 대회는 처음부터 판매 중으로 저장한다 (CRU-36)
 *                           E2E·부하 테스트에서 판매 시작일을 DB로 당기고 판매 상태 배치(5분)를 기다리지 않으려는 것이다.
 *                           정책(오늘로부터 이틀 뒤부터)을 건너뛰는 옵션이라 평소에는 꺼 두고, 수정에는 적용하지 않는다
 */
@ConfigurationProperties("product.race")
public record RaceSaleProperties(boolean sameDaySaleEnabled) {
}
