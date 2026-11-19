package com.bukang.order.out.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KitTrackingLinkTest {
	private static final String BASE_URL = "https://link.tracker.delivery/track";

	@Test
	@DisplayName("Client ID·택배사 ID·송장 번호를 URL 인코딩해 공식 Tracking Link를 만든다")
	void encodesValues() {
		KitTrackingLink link = new KitTrackingLink(BASE_URL, "client id&1");

		assertThat(link.urlOf("kr.cjlogistics", "6849-1234 5678&x=1"))
			.isEqualTo(BASE_URL + "?client_id=client+id%261&carrier_id=kr.cjlogistics"
				+ "&tracking_number=6849-1234+5678%26x%3D1");
	}

	@Test
	@DisplayName("Client ID가 없거나 택배사 ID·송장 번호가 없으면(발송 전, 옛 배송) 링크를 만들지 않는다")
	void nullWhenMissing() {
		assertThat(new KitTrackingLink(BASE_URL, "").urlOf("kr.cjlogistics", "684912345678")).isNull();
		assertThat(new KitTrackingLink(BASE_URL, " ").urlOf("kr.cjlogistics", "684912345678")).isNull();

		KitTrackingLink link = new KitTrackingLink(BASE_URL, "client");
		assertThat(link.urlOf(null, "684912345678")).isNull();
		assertThat(link.urlOf("kr.cjlogistics", null)).isNull();
	}
}
