package com.bukang.payout.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 집합 방식: 양쪽을 HashSet으로 만들어 차집합을 구한다. ID가 흩어져 있어도 메모리는 장수에만 비례한다
 * 운영 배치의 기본값이다. payout.reconcile.method가 없거나 set이면 이 빈을 쓴다 (비트맵과 비교하는 부하 테스트용 설정)
 */
@Component
@ConditionalOnProperty(name = TicketDiffer.METHOD_PROPERTY, havingValue = "set", matchIfMissing = true)
public class SetTicketDiffer implements TicketDiffer {
	@Override
	public TicketDiff diff(Collection<Long> soldIds, Collection<Long> payoutIds) {
		Set<Long> sold = new HashSet<>(soldIds);
		Set<Long> payout = new HashSet<>(payoutIds);
		return new TicketDiff(sortedMinus(sold, payout), sortedMinus(payout, sold));
	}

	private static List<Long> sortedMinus(Set<Long> from, Set<Long> minus) {
		return from.stream().filter(id -> !minus.contains(id)).sorted().toList();
	}
}
