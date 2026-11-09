package com.bukang.payout.domain;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * 비트맵 방식: 참가권 ID마다 비트 하나(위치 = ID - 가장 작은 ID)를 켜고, 판매 XOR 정산으로 어긋난 위치를 찾는다
 * ID가 촘촘하면 장수 / 8바이트 정도로 작지만, 흩어지면 가장 작은 ID부터 가장 큰 ID까지 비트가 필요하다
 * BitSet의 위치는 int라 범위가 int를 넘으면 그 대회는 집합 방식으로 비교한다 (11/5 결정)
 */
public class BitmapTicketDiffer implements TicketDiffer {
	private final TicketDiffer fallback = new SetTicketDiffer();

	@Override
	public TicketDiff diff(Collection<Long> soldIds, Collection<Long> payoutIds) {
		if (soldIds.isEmpty() && payoutIds.isEmpty()) {
			return new TicketDiff(List.of(), List.of());
		}
		long minId = Stream.concat(soldIds.stream(), payoutIds.stream()).mapToLong(Long::longValue).min().orElseThrow();
		long maxId = Stream.concat(soldIds.stream(), payoutIds.stream()).mapToLong(Long::longValue).max().orElseThrow();
		if (maxId - minId >= Integer.MAX_VALUE) {
			return fallback.diff(soldIds, payoutIds);
		}
		BitSet sold = bitmapOf(soldIds, minId);
		BitSet payout = bitmapOf(payoutIds, minId);
		BitSet mismatched = (BitSet)sold.clone();
		mismatched.xor(payout);

		List<Long> missing = new ArrayList<>();
		List<Long> extra = new ArrayList<>();
		mismatched.stream().forEach(index -> (sold.get(index) ? missing : extra).add(minId + index));
		return new TicketDiff(missing, extra);
	}

	private static BitSet bitmapOf(Collection<Long> ids, long minId) {
		BitSet bitmap = new BitSet();
		ids.forEach(id -> bitmap.set((int)(id - minId)));
		return bitmap;
	}
}
