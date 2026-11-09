package com.bukang.order.out.kit;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 어댑터: 상품 서비스를 띄우지 않고 개발할 때, 어떤 대회든 키트 옵션이 없다고 답한다
 * 프론트는 옵션 목록을 상품에서 직접 받으므로, 상품이 없으면 고를 옵션도 없다. 결제는 옵션 없이(kitOptionIds 비움) 된다
 * order.clients.product.mode=fake(기본값)일 때만 쓰고, http면 HttpKitOptionAdapter가 부른다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeKitOptionAdapter implements KitOptionPort {
	@Override
	public List<KitOption> findKitOptions(long raceId) {
		return List.of();
	}
}
