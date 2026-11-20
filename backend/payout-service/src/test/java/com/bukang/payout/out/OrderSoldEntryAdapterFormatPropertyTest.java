package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 부하 테스트 compose가 형식을 소문자(PAYOUT_SOLDENTRIES_FORMAT: bitset)로 넘겨도 앱이 뜨고 그 형식을 고르는지 본다
 * 형식 설정은 enum으로 바로 받으므로, 대소문자를 가리면 기동에서 실패한다
 */
@SpringBootTest(properties = OrderSoldEntryAdapter.FORMAT_PROPERTY + "=bitset")
@ActiveProfiles("test")
class OrderSoldEntryAdapterFormatPropertyTest {

	@Autowired
	private OrderSoldEntryAdapter adapter;

	@Test
	@DisplayName("소문자 bitset 설정으로 앱이 뜨고 어댑터가 BITSET을 고른다")
	void lowercaseFormatBinds() {
		assertThat(ReflectionTestUtils.getField(adapter, "format")).isEqualTo(OrderSoldEntryAdapter.Format.BITSET);
	}
}
