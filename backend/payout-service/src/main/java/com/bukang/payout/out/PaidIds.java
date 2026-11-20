package com.bukang.payout.out;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;
import java.util.function.LongConsumer;

import org.roaringbitmap.RoaringBitmap;

/**
 * 코스 하나의 결제 완료 주문 상품 ID. 주문 응답 형식(IDS · BITSET · ROARING)대로 들고 있다가, 정산 줄을 만들 때 하나씩 꺼낸다
 * 비트맵은 풀어서 목록으로 바꾸지 않는다. 20만 장이면 목록이 수 MB라, 꺼내는 순간에만 ID를 만든다
 */
public interface PaidIds {
	int count();

	// 주문 상품 ID 오름차순
	void forEach(LongConsumer action);

	static PaidIds of(List<Long> ids) {
		return new ListIds(ids);
	}

	// 위치 = ID − base (주문 SoldIdsEncoder와 같은 규칙)
	static PaidIds ofBitSet(long base, byte[] bytes) {
		return new BitSetIds(base, BitSet.valueOf(bytes));
	}

	static PaidIds ofRoaring(long base, byte[] bytes) {
		RoaringBitmap bitmap = new RoaringBitmap();
		try {
			bitmap.deserialize(ByteBuffer.wrap(bytes));
		} catch (IOException | RuntimeException exception) {
			throw new IllegalArgumentException("결제 완료 ID 비트맵(ROARING)을 읽지 못했습니다.", exception);
		}
		return new RoaringIds(base, bitmap);
	}

	record ListIds(List<Long> ids) implements PaidIds {
		@Override
		public int count() {
			return ids.size();
		}

		@Override
		public void forEach(LongConsumer action) {
			ids.forEach(action::accept);
		}
	}

	record BitSetIds(long base, BitSet bits) implements PaidIds {
		@Override
		public int count() {
			return bits.cardinality();
		}

		@Override
		public void forEach(LongConsumer action) {
			bits.stream().forEach(index -> action.accept(base + index));
		}
	}

	record RoaringIds(long base, RoaringBitmap bitmap) implements PaidIds {
		@Override
		public int count() {
			return bitmap.getCardinality();
		}

		@Override
		public void forEach(LongConsumer action) {
			bitmap.forEach((int index) -> action.accept(base + index));
		}
	}
}
