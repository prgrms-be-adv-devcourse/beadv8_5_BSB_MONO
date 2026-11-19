package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.order.out.kit.CachingKitOptionPort;
import com.bukang.order.support.StubKitOptionPort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPortConfig;

/**
 * 키트 옵션 목록을 기억해도(CRU-45) 판매 수는 기억하지 않는다. 준비 수량 1개 옵션을 두 주문이 사면 두 번째는
 * 상품을 다시 부르지 않고도 주문 DB의 판매 수로 품절(409)이 된다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	OrderPayKitOptionCacheTest.CacheConfig.class})
class OrderPayKitOptionCacheTest extends OrderPaySplitTestSupport {
	static final AtomicInteger PRODUCT_CALLS = new AtomicInteger();

	@Test
	@DisplayName("두 번째 결제는 상품을 부르지 않고, 판매 수는 주문 DB로 세어 준비 수량을 넘지 않는다")
	void cachedOptionsStillLimitSold() throws Exception {
		kitOptionPort.clear();
		kitOptionPort.add(RACE_ID, SHIRT_M, "T-shirt", "M", 1);
		long first = createOrder(1);
		long second = createOrder(2);
		PRODUCT_CALLS.set(0);

		pay(1, first).andExpect(status().isOk());
		pay(2, second).andExpect(status().isConflict())
			.andExpect(jsonPath("$.data.soldOut[0].kitOptionId").value(SHIRT_M));

		assertThat(PRODUCT_CALLS).hasValue(1);
		assertThat(sold(SHIRT_M)).isEqualTo(1);
	}

	@TestConfiguration
	static class CacheConfig {
		@Bean
		StubKitOptionPort stubKitOptionPort() {
			return new StubKitOptionPort();
		}

		@Bean
		@Primary
		CachingKitOptionPort cachingKitOptionPort(StubKitOptionPort stub) {
			return new CachingKitOptionPort(raceId -> {
				PRODUCT_CALLS.incrementAndGet();
				return stub.findKitOptions(raceId);
			}, Duration.ofMinutes(1), System::nanoTime);
		}
	}
}
