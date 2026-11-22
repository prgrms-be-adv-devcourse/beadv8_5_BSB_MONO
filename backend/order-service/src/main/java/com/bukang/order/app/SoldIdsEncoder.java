package com.bukang.order.app;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;

import org.roaringbitmap.RoaringBitmap;

import com.bukang.order.dto.SoldEntriesDto;
import com.bukang.order.dto.SoldEntriesFormat;

/**
 * 코스 묶음의 결제 완료 ID 목록(오름차순)을 비트맵으로 바꾼다. 위치는 "ID − 가장 작은 ID"이고 가장 작은 ID를 paidBase로 함께 보낸다
 * 정산 서비스의 OrderSoldEntryAdapter가 같은 규칙으로 푼다
 */
final class SoldIdsEncoder {
	private SoldIdsEncoder() {
	}

	// shortcut: 위치가 int라 가장 작은 ID와 가장 큰 ID 차이가 int를 넘는 코스는 IDS로 보낸다. 넘는 대회가 생기면 Roaring64로 바꾼다
	static SoldEntriesDto.Course encode(SoldEntriesDto.Course course, SoldEntriesFormat format) {
		List<Long> ids = course.paidIds();
		if (format == SoldEntriesFormat.IDS || ids.isEmpty()
			|| ids.getLast() - ids.getFirst() >= Integer.MAX_VALUE) {
			return course;
		}
		long base = ids.getFirst();
		byte[] bytes = format == SoldEntriesFormat.BITSET ? bitSetOf(ids, base) : roaringOf(ids, base);
		return SoldEntriesDto.Course.bitmap(course.raceCourseId(), course.price(), format, base, ids.size(),
			Base64.getEncoder().encodeToString(bytes), course.blockingIds());
	}

	private static byte[] bitSetOf(List<Long> ids, long base) {
		BitSet bits = new BitSet();
		ids.forEach(id -> bits.set((int)(id - base)));
		return bits.toByteArray();
	}

	private static byte[] roaringOf(List<Long> ids, long base) {
		RoaringBitmap bitmap = new RoaringBitmap();
		ids.forEach(id -> bitmap.add((int)(id - base)));
		// 이어진 ID 구간은 (시작, 길이)로 줄인다
		bitmap.runOptimize();
		ByteBuffer buffer = ByteBuffer.allocate(bitmap.serializedSizeInBytes());
		bitmap.serialize(buffer);
		return buffer.array();
	}
}
