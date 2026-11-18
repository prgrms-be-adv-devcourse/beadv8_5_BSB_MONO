package com.bukang.order.out.delivery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bukang.order.dto.KitTrackingDto;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.dto.OrderItemDto;

/**
 * 구매자 「배송 조회」 버튼이 여는 Delivery Tracker 공식 Tracking Link (CRU-38, https://tracker.delivery/docs/tracking-link)
 * Client ID를 프론트에 두지 않으려고 서버가 주소를 만든다. Secret은 쓰지 않는다
 * Client ID가 비었거나 택배사 ID·송장 번호가 없는 배송(발송 전, BUK-55 전에 보낸 배송)이면 null이다
 */
@Component
public class KitTrackingLink {
	private final String baseUrl;
	private final String clientId;

	public KitTrackingLink(
		@Value("${order.clients.delivery-tracker.link-base-url:https://link.tracker.delivery/track}") String baseUrl,
		@Value("${order.clients.delivery-tracker.client-id:}") String clientId) {
		this.baseUrl = baseUrl;
		this.clientId = clientId;
	}

	public OrderDto addTo(OrderDto order) {
		return order.withItems(order.getItems().stream().map(this::addTo).toList());
	}

	// 택배사 ID는 조회 칸(배송에 저장된 carrierId)에서 꺼낸다
	public OrderItemDto addTo(OrderItemDto item) {
		KitTrackingDto tracking = item.getKitTracking();
		String carrierId = tracking == null ? null : tracking.carrierId();
		return item.withKitTrackingUrl(urlOf(carrierId, item.getKitTrackingNumber()));
	}

	String urlOf(String carrierId, String trackingNumber) {
		if (isBlank(clientId) || isBlank(carrierId) || isBlank(trackingNumber)) {
			return null;
		}
		return baseUrl + "?client_id=" + encode(clientId) + "&carrier_id=" + encode(carrierId)
			+ "&tracking_number=" + encode(trackingNumber);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
