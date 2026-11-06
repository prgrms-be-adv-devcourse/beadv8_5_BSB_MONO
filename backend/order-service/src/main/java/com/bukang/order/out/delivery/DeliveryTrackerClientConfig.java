package com.bukang.order.out.delivery;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.order.out.InternalHttpClients;

/**
 * Delivery Tracker를 실제로 부를 때만 켠다 (order.clients.delivery-tracker.mode=http, BUK-55)
 * 기본값(fake)이면 FakeDeliveryTrackingAdapter가 답해 키 없이 개발할 수 있다
 * 키는 환경 변수(DELIVERY_TRACKER_CLIENT_ID·SECRET)로만 받는다. 비어 있어도 앱은 뜨고 조회만 실패한다
 */
@Configuration
@ConditionalOnProperty(prefix = "order.clients.delivery-tracker", name = "mode", havingValue = "http")
public class DeliveryTrackerClientConfig {
	@Bean
	public HttpDeliveryTrackingAdapter httpDeliveryTrackingAdapter(
		@Value("${order.clients.delivery-tracker.base-url}") String baseUrl,
		@Value("${order.clients.delivery-tracker.timeout}") Duration timeout,
		@Value("${order.clients.delivery-tracker.client-id:}") String clientId,
		@Value("${order.clients.delivery-tracker.client-secret:}") String clientSecret
	) {
		return new HttpDeliveryTrackingAdapter(InternalHttpClients.create(baseUrl, timeout), clientId, clientSecret,
			HttpDeliveryTrackingAdapter.RETRY_DELAYS);
	}
}
