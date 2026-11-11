package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jol.info.GraphLayout;

/**
 * 대사 비교 방식의 메모리 크기를 JOL로 잰다 (부하 테스트 S4, 비트맵 vs 집합)
 * 측정용이라 평소 테스트에서는 건너뛴다. PAYOUT_MEASURE=true 환경 변수로 돌리고, 결과는 테스트 표준 출력(리포트 XML)에 남긴다
 * 집합 방식은 HashSet 2개(판매 · 정산), 비트맵 방식은 BitSet 3개(판매 · 정산 · XOR 결과)를 만든다 (SetEntryDiffer · BitmapEntryDiffer와 같음)
 * 참가권 ID는 촘촘하다고 가정한다(주문 상품 ID 연속). 흩어지면 비트맵이 커진다
 */
@EnabledIfEnvironmentVariable(named = "PAYOUT_MEASURE", matches = "true")
class EntryDifferMemoryTest {
	private static final long FIRST_ID = 1_000_000L;

	@ParameterizedTest
	@ValueSource(ints = {30_000, 300_000, 1_000_000})
	@DisplayName("장수별 집합 · 비트맵 크기")
	void measure(int count) {
		List<Long> ids = LongStream.range(FIRST_ID, FIRST_ID + count).boxed().toList();

		Set<Long> sold = new HashSet<>(ids);
		Set<Long> payout = new HashSet<>(ids);
		long setBytes = GraphLayout.parseInstance(sold, payout).totalSize();

		BitSet soldBits = bitmapOf(ids);
		BitSet payoutBits = bitmapOf(ids);
		BitSet mismatched = (BitSet)soldBits.clone();
		mismatched.xor(payoutBits);
		long bitmapBytes = GraphLayout.parseInstance(soldBits, payoutBits, mismatched).totalSize();

		System.out.printf("EntryDifferMemory count=%d set=%d bytes bitmap=%d bytes ratio=%.1f%n",
			count, setBytes, bitmapBytes, (double)setBytes / bitmapBytes);
		assertThat(bitmapBytes).isLessThan(setBytes);
	}

	private static BitSet bitmapOf(List<Long> ids) {
		BitSet bitmap = new BitSet();
		ids.forEach(id -> bitmap.set((int)(id - FIRST_ID)));
		return bitmap;
	}
}
