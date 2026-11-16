package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// 집합 방식과 비트맵 방식은 같은 입력에 같은 결과를 낸다 (AC-67-9). 모든 케이스를 두 방식으로 돌린다
class EntryDifferTest {
	static Stream<EntryDiffer> differs() {
		return Stream.of(new SetEntryDiffer(), new BitmapEntryDiffer());
	}

	@ParameterizedTest
	@MethodSource("differs")
	@DisplayName("TC-67-5-2 · 9-1 ID가 촘촘할 때: 판매에만 있는 참가권은 빠짐, 정산에만 있는 참가권은 더 들어감")
	void dense(EntryDiffer differ) {
		EntryDiff diff = differ.diff(List.of(101L, 102L, 103L, 104L, 105L), List.of(101L, 102L, 104L, 105L, 106L));

		assertThat(diff.missing()).containsExactly(103L);
		assertThat(diff.extra()).containsExactly(106L);
	}

	@ParameterizedTest
	@MethodSource("differs")
	@DisplayName("TC-67-9-1 ID가 흩어져 있어도 같은 결과")
	void scattered(EntryDiffer differ) {
		EntryDiff diff = differ.diff(List.of(1_000_001L, 5_000_000L, 9_999_999L), List.of(1_000_001L, 9_999_999L));

		assertThat(diff.missing()).containsExactly(5_000_000L);
		assertThat(diff.extra()).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("differs")
	@DisplayName("TC-67-12-1 500장이 빠지면 500개를 작은 ID부터 돌려준다")
	void manyMissing(EntryDiffer differ) {
		List<Long> sold = LongStream.rangeClosed(1, 500).boxed().toList();

		EntryDiff diff = differ.diff(sold, List.of());

		assertThat(diff.missing()).isEqualTo(sold);
		assertThat(diff.extra()).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("differs")
	@DisplayName("TC-67-5-1 판매가 없고 정산에만 있으면 모두 더 들어감, 둘 다 같으면 어긋남 없음")
	void extraOnlyAndSame(EntryDiffer differ) {
		assertThat(differ.diff(List.of(), List.of(106L)).extra()).containsExactly(106L);
		assertThat(differ.diff(List.of(1L, 2L), List.of(2L, 1L)).isEmpty()).isTrue();
		assertThat(differ.diff(List.of(), List.of()).isEmpty()).isTrue();
	}

	@ParameterizedTest
	@MethodSource("differs")
	@DisplayName("ID 범위가 int를 넘어도 같은 결과 (비트맵은 그 대회만 집합으로 비교, 11/5 결정)")
	void rangeOverInt(EntryDiffer differ) {
		EntryDiff diff = differ.diff(List.of(1L, 5_000_000_000L), List.of(5_000_000_000L, 9_000_000_000L));

		assertThat(diff.missing()).containsExactly(1L);
		assertThat(diff.extra()).containsExactly(9_000_000_000L);
	}
}
