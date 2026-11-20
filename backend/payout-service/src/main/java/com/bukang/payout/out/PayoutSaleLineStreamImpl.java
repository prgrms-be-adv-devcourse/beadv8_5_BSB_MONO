package com.bukang.payout.out;

import java.util.Collection;
import java.util.stream.Stream;

import org.hibernate.jpa.HibernateHints;
import org.springframework.beans.factory.annotation.Value;

import com.bukang.payout.domain.Payout;

import jakarta.persistence.EntityManager;

/**
 * MySQL 드라이버는 기본으로 결과를 끝까지 메모리에 받는다. fetch size Integer.MIN_VALUE면 한 줄씩 흘려 받는다
 * useCursorFetch=true(1000줄씩)는 서버 prepared statement를 함께 켜서 생성 일괄 INSERT가 약 3초 느려져 쓰지 않는다 (11/19 측정)
 * H2는 음수 fetch size를 거부하므로 테스트는 설정으로 0(드라이버 기본)을 준다
 */
class PayoutSaleLineStreamImpl implements PayoutSaleLineStream {
	private static final String SALE_LINE_IDS = """
		select i.orderItemId from PayoutItem i
		where i.payout in :payouts and i.recordType = com.bukang.payout.domain.PayoutRecordType.판매
		""";

	private final EntityManager entityManager;
	private final int fetchSize;

	PayoutSaleLineStreamImpl(EntityManager entityManager,
		@Value("${payout.reconcile.stream-fetch-size:-2147483648}") int fetchSize) {
		this.entityManager = entityManager;
		this.fetchSize = fetchSize;
	}

	@Override
	public Stream<Long> streamSaleLineIds(Collection<Payout> payouts) {
		return entityManager.createQuery(SALE_LINE_IDS, Long.class)
			.setParameter("payouts", payouts)
			.setHint(HibernateHints.HINT_FETCH_SIZE, fetchSize)
			.getResultStream();
	}
}
