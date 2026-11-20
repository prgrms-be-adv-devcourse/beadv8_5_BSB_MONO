package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.roaringbitmap.RoaringBitmap;

import com.bukang.payout.domain.EntryDiff;

// 받은 형식(IDS · BITSET · ROARING)이 달라도 대사의 빠짐 · 더 들어감은 같다 (AC-67-9, CRU-57). 모든 케이스를 세 형식으로 돌린다
class OrderItemIdsTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);

	enum Format {
		IDS, BITSET, ROARING
	}

	// 코스마다 ID 목록. 받은 형식대로 PaidIds를 만들어 판매 쪽 모음으로 합친다
	private static OrderItemIds sold(Format format, List<List<Long>> courses) {
		return new SoldCourses(courses.stream()
			.map(ids -> new SoldCourses.Course(new SoldCourses.Key(1, 7, RACE_DATE, (long)ids.hashCode(), 10_000),
				paidIds(format, ids), List.of()))
			.toList()).orderItemIds();
	}

	private static PaidIds paidIds(Format format, List<Long> ids) {
		if (format == Format.IDS || ids.isEmpty()) {
			return PaidIds.of(ids);
		}
		long base = ids.stream().mapToLong(Long::longValue).min().orElseThrow();
		if (format == Format.BITSET) {
			BitSet bits = new BitSet();
			ids.forEach(id -> bits.set((int)(id - base)));
			return new PaidIds.BitSetIds(base, bits);
		}
		RoaringBitmap bitmap = new RoaringBitmap();
		ids.forEach(id -> bitmap.add((int)(id - base)));
		return new PaidIds.RoaringIds(base, bitmap);
	}

	private static EntryDiff diff(OrderItemIds sold, List<Long> payoutIds) {
		OrderItemIds payout = sold.emptyLike();
		payoutIds.forEach(payout::add);
		return sold.diff(payout);
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("TC-67-5-2 · 9-1 ID가 촘촘할 때: 판매에만 있는 참가권은 빠짐, 정산에만 있는 참가권은 더 들어감")
	void dense(Format format) {
		EntryDiff diff = diff(sold(format, List.of(List.of(101L, 102L, 103L, 104L, 105L))),
			List.of(101L, 102L, 104L, 105L, 106L));

		assertThat(diff.missing()).containsExactly(103L);
		assertThat(diff.extra()).containsExactly(106L);
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("TC-67-9-1 ID가 흩어져 있어도 같은 결과")
	void scattered(Format format) {
		EntryDiff diff = diff(sold(format, List.of(List.of(1_000_001L, 5_000_000L, 9_999_999L))),
			List.of(1_000_001L, 9_999_999L));

		assertThat(diff.missing()).containsExactly(5_000_000L);
		assertThat(diff.extra()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("TC-67-12-1 500장이 빠지면 500개를 작은 ID부터 돌려준다")
	void manyMissing(Format format) {
		List<Long> ids = LongStream.rangeClosed(1, 500).boxed().toList();

		EntryDiff diff = diff(sold(format, List.of(ids)), List.of());

		assertThat(diff.missing()).isEqualTo(ids);
		assertThat(diff.extra()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("TC-67-5-1 판매가 없고 정산에만 있으면 모두 더 들어감, 둘 다 같으면 어긋남 없음")
	void extraOnlyAndSame(Format format) {
		assertThat(diff(sold(format, List.of()), List.of(106L)).extra()).containsExactly(106L);
		assertThat(diff(sold(format, List.of(List.of(1L, 2L))), List.of(2L, 1L)).isEmpty()).isTrue();
		assertThat(diff(sold(format, List.of()), List.of()).isEmpty()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("코스가 여러 개면 코스 ID들을 하나로 합쳐 한 번에 비교한다 (코스마다 기준 ID가 달라도)")
	void manyCourses(Format format) {
		OrderItemIds sold = sold(format, List.of(List.of(500L, 502L), List.of(100L, 101L), List.of(900L)));

		EntryDiff diff = diff(sold, List.of(100L, 101L, 500L, 900L, 901L));

		assertThat(sold.size()).isEqualTo(5);
		assertThat(diff.missing()).containsExactly(502L);
		assertThat(diff.extra()).containsExactly(901L);
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("정산 쪽 ID가 판매 비트맵 범위 밖(기준보다 작거나 int를 넘음)이어도 더 들어감으로 잡는다")
	void payoutOutsideRange(Format format) {
		EntryDiff diff = diff(sold(format, List.of(List.of(100L, 101L))), List.of(5L, 100L, 101L, 9_000_000_000L));

		assertThat(diff.missing()).isEmpty();
		assertThat(diff.extra()).containsExactly(5L, 9_000_000_000L);
	}

	@Test
	@DisplayName("판매 쪽 ID 범위가 int를 넘으면 비트맵 형식이어도 집합으로 비교한다 (11/5 결정)")
	void soldRangeOverInt() {
		// 주문은 범위가 int를 넘는 코스를 IDS로 보내므로, 비트맵 코스와 IDS 코스가 한 응답에 섞인다
		OrderItemIds sold = new SoldCourses(List.of(
			new SoldCourses.Course(new SoldCourses.Key(1, 7, RACE_DATE, 1L, 10_000), paidIds(Format.BITSET,
				List.of(1L)), List.of()),
			new SoldCourses.Course(new SoldCourses.Key(1, 7, RACE_DATE, 2L, 10_000),
				PaidIds.of(List.of(5_000_000_000L)), List.of()))).orderItemIds();

		EntryDiff diff = diff(sold, List.of(5_000_000_000L, 9_000_000_000L));

		assertThat(sold).isInstanceOf(OrderItemIds.HashIds.class);
		assertThat(diff.missing()).containsExactly(1L);
		assertThat(diff.extra()).containsExactly(9_000_000_000L);
	}

	@Test
	@DisplayName("받은 형식대로 모음을 고른다: 비트맵이 있으면 그 비트맵, 목록뿐이면 집합")
	void picksReceivedFormat() {
		List<Long> ids = List.of(10L, 11L);

		assertThat(sold(Format.IDS, List.of(ids))).isInstanceOf(OrderItemIds.HashIds.class);
		assertThat(sold(Format.BITSET, List.of(ids))).isInstanceOf(OrderItemIds.BitIds.class);
		assertThat(sold(Format.ROARING, List.of(ids))).isInstanceOf(OrderItemIds.RoaringBitIds.class);
	}

	@Test
	@DisplayName("미루는 참가권(취소 중)도 판매 쪽 ID에 들어간다")
	void includesBlockingIds() {
		OrderItemIds sold = new SoldCourses(List.of(new SoldCourses.Course(
			new SoldCourses.Key(1, 7, RACE_DATE, 1L, 10_000), paidIds(Format.BITSET, List.of(10L)), List.of(12L))))
			.orderItemIds();

		EntryDiff diff = diff(sold, List.of(10L));

		assertThat(diff.missing()).containsExactly(12L);
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	@DisplayName("같은 ID가 두 번 오면 예외다 (주문 응답의 같은 참가권 두 줄을 조용히 합치지 않는다)")
	void duplicatedIdThrows(Format format) {
		OrderItemIds sold = sold(format, List.of(List.of(10L, 11L)));

		assertThatThrownBy(() -> sold.add(10L)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> sold(format, List.of(List.of(10L), List.of(10L))))
			.isInstanceOf(IllegalStateException.class);
	}
}
