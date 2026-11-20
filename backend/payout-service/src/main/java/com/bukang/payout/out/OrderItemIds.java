package com.bukang.payout.out;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.roaringbitmap.RoaringBitmap;

import com.bukang.payout.domain.EntryDiff;

/**
 * 최종 대사가 맞춰 보는 주문 상품 ID 모음 (CRU-57)
 * 판매 쪽은 주문 응답 형식(IDS · BITSET · ROARING) 그대로 합치고, 정산 쪽은 emptyLike()로 같은 형식 · 같은 기준 ID를 만들어 DB에서 채운다
 * 두 모음의 차이는 형식마다 한 번에 구한다: 집합은 차집합, 비트맵은 XOR 한 번
 * 비트맵의 위치는 ID - base(판매 쪽 가장 작은 ID)다. 정산 쪽 ID가 그 범위 밖이면 판매에 없는 ID이므로 따로 담아 더 들어감으로 본다
 */
public sealed interface OrderItemIds {
	// 비트맵 위치는 int라, 판매 쪽 ID 범위가 이보다 넓으면 집합으로 비교한다 (11/5 결정)
	long MAX_RANGE = Integer.MAX_VALUE;

	// 같은 ID를 두 번 넣으면 예외다. 주문 응답에 같은 참가권이 두 줄 오면 대사를 멈추고 알_수_없는_실패로 남긴다 (PR #330 리뷰)
	void add(long id);

	int size();

	// 같은 형식 · 같은 기준 ID의 빈 모음
	OrderItemIds emptyLike();

	/**
	 * this = 판매, other = 정산. 빠짐(판매에만) · 더 들어감(정산에만)을 작은 ID부터 돌려준다
	 * other는 this.emptyLike()로 만든 모음이어야 한다
	 */
	EntryDiff diff(OrderItemIds other);

	static OrderItemIds hashSet() {
		return new HashIds(new HashSet<>());
	}

	/**
	 * 판매 쪽 모음을 고른다. 받은 형식에 비트맵이 하나라도 있으면 그 비트맵 형식, 아니면 집합
	 * 주문은 범위가 int를 넘는 코스를 IDS로 보내므로 한 응답 안에 형식이 섞일 수 있다
	 */
	static OrderItemIds emptyFor(List<PaidIds> received, long minId, long maxId) {
		if (minId > maxId || maxId - minId >= MAX_RANGE) {
			return hashSet();
		}
		for (PaidIds ids : received) {
			if (ids instanceof PaidIds.BitSetIds) {
				return new BitIds(minId, new BitSet(), new HashSet<>());
			}
			if (ids instanceof PaidIds.RoaringIds) {
				return new RoaringBitIds(minId, new RoaringBitmap(), new HashSet<>());
			}
		}
		return hashSet();
	}

	// IDS: HashSet 두 개의 차집합 (예전 SetEntryDiffer와 같은 방식)
	record HashIds(Set<Long> ids) implements OrderItemIds {
		@Override
		public void add(long id) {
			if (!ids.add(id)) {
				throw duplicated(id);
			}
		}

		@Override
		public int size() {
			return ids.size();
		}

		@Override
		public OrderItemIds emptyLike() {
			return hashSet();
		}

		@Override
		public EntryDiff diff(OrderItemIds other) {
			Set<Long> payout = ((HashIds)other).ids;
			return new EntryDiff(sortedMinus(ids, payout), sortedMinus(payout, ids));
		}
	}

	// BITSET: 판매 XOR 정산의 켜진 위치가 어긋난 ID다. 판매 쪽에서 켜져 있으면 빠짐, 아니면 더 들어감
	record BitIds(long base, BitSet bits, Set<Long> outside) implements OrderItemIds {
		@Override
		public void add(long id) {
			long index = id - base;
			if (index < 0 || index >= MAX_RANGE) {
				if (!outside.add(id)) {
					throw duplicated(id);
				}
			} else if (bits.get((int)index)) {
				throw duplicated(id);
			} else {
				bits.set((int)index);
			}
		}

		@Override
		public int size() {
			return bits.cardinality() + outside.size();
		}

		@Override
		public OrderItemIds emptyLike() {
			return new BitIds(base, new BitSet(), new HashSet<>());
		}

		@Override
		public EntryDiff diff(OrderItemIds other) {
			BitIds payout = (BitIds)other;
			BitSet mismatched = (BitSet)bits.clone();
			mismatched.xor(payout.bits);
			List<Long> missing = new ArrayList<>();
			List<Long> extra = new ArrayList<>();
			mismatched.stream().forEach(index -> (bits.get(index) ? missing : extra).add(base + index));
			return withOutside(missing, extra, outside, payout.outside);
		}
	}

	// ROARING: BITSET과 같은 방식을 RoaringBitmap으로 한다 (위치는 0 이상 int 범위라 부호 없는 정수 문제가 없다)
	record RoaringBitIds(long base, RoaringBitmap bitmap, Set<Long> outside) implements OrderItemIds {
		@Override
		public void add(long id) {
			long index = id - base;
			if (index < 0 || index >= MAX_RANGE) {
				if (!outside.add(id)) {
					throw duplicated(id);
				}
			} else if (!bitmap.checkedAdd((int)index)) {
				throw duplicated(id);
			}
		}

		@Override
		public int size() {
			return bitmap.getCardinality() + outside.size();
		}

		@Override
		public OrderItemIds emptyLike() {
			return new RoaringBitIds(base, new RoaringBitmap(), new HashSet<>());
		}

		@Override
		public EntryDiff diff(OrderItemIds other) {
			RoaringBitIds payout = (RoaringBitIds)other;
			RoaringBitmap mismatched = RoaringBitmap.xor(bitmap, payout.bitmap);
			List<Long> missing = new ArrayList<>();
			List<Long> extra = new ArrayList<>();
			mismatched.forEach((int index) -> (bitmap.contains(index) ? missing : extra).add(base + index));
			return withOutside(missing, extra, outside, payout.outside);
		}
	}

	private static IllegalStateException duplicated(long id) {
		return new IllegalStateException("같은 주문 상품 ID가 두 번 나왔습니다. orderItemId=" + id);
	}

	private static List<Long> sortedMinus(Set<Long> from, Set<Long> minus) {
		return from.stream().filter(id -> !minus.contains(id)).sorted().toList();
	}

	// 비트맵 범위 밖 ID를 더한다. 밖 ID가 있을 때만 다시 정렬한다
	private static EntryDiff withOutside(List<Long> missing, List<Long> extra, Set<Long> soldOutside,
		Set<Long> payoutOutside) {
		List<Long> missingOutside = sortedMinus(soldOutside, payoutOutside);
		List<Long> extraOutside = sortedMinus(payoutOutside, soldOutside);
		if (!missingOutside.isEmpty()) {
			missing.addAll(missingOutside);
			missing.sort(null);
		}
		if (!extraOutside.isEmpty()) {
			extra.addAll(extraOutside);
			extra.sort(null);
		}
		return new EntryDiff(missing, extra);
	}
}
