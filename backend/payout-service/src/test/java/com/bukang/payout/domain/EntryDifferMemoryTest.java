package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
 *
 * 두 경우를 잰다
 * - 촘촘함: 참가권 ID가 연속이다. 비트맵이 집합보다 작아야 한다
 * - 손익분기 흩어짐: 비트맵 3개 크기(3 × 범위 / 8바이트)가 촘촘할 때의 집합 크기 S와 같아지는 범위 R = 8S / 3에
 *   ID를 R / 장수 간격으로 흩뿌린다. 두 크기가 비슷해야 하고(±10%), ID 간격이 이보다 촘촘해야 비트맵이 집합보다 작다
 */
@EnabledIfEnvironmentVariable(named = "PAYOUT_MEASURE", matches = "true")
class EntryDifferMemoryTest {
	private static final long FIRST_ID = 1_000_000L;
	private static final double BREAK_EVEN_TOLERANCE = 0.1;

	@ParameterizedTest
	@ValueSource(ints = {30_000, 300_000, 1_000_000})
	@DisplayName("장수별 집합 · 비트맵 크기 (촘촘함 · 손익분기 흩어짐)")
	void measure(int count) {
		List<Long> denseIds = idsWithGap(count, 1);
		long setBytes = setBytes(denseIds);
		long denseBitmapBytes = bitmapBytes(denseIds);
		System.out.printf("EntryDifferMemory dense count=%d gap=1 set=%d bytes bitmap=%d bytes ratio=%.1f%n",
			count, setBytes, denseBitmapBytes, (double)setBytes / denseBitmapBytes);
		assertThat(denseBitmapBytes).isLessThan(setBytes);

		// 집합 크기는 장수에만 달려 있어(HashSet 용량 · Long 개수가 같다) 흩어진 경우에도 촘촘할 때의 S를 그대로 쓴다
		long breakEvenRange = setBytes * 8 / 3;
		long gap = breakEvenRange / count;
		long scatteredBitmapBytes = bitmapBytes(idsWithGap(count, gap));
		System.out.printf("EntryDifferMemory breakEven count=%d gap=%d set=%d bytes bitmap=%d bytes ratio=%.2f%n",
			count, gap, setBytes, scatteredBitmapBytes, (double)setBytes / scatteredBitmapBytes);
		assertThat((double)scatteredBitmapBytes / setBytes).isCloseTo(1.0, within(BREAK_EVEN_TOLERANCE));
	}

	private static List<Long> idsWithGap(int count, long gap) {
		return LongStream.range(0, count).mapToObj(index -> FIRST_ID + index * gap).toList();
	}

	private static long setBytes(List<Long> ids) {
		Set<Long> sold = new HashSet<>(ids);
		Set<Long> payout = new HashSet<>(ids);
		return GraphLayout.parseInstance(sold, payout).totalSize();
	}

	private static long bitmapBytes(List<Long> ids) {
		BitSet soldBits = bitmapOf(ids);
		BitSet payoutBits = bitmapOf(ids);
		BitSet mismatched = (BitSet)soldBits.clone();
		mismatched.xor(payoutBits);
		return GraphLayout.parseInstance(soldBits, payoutBits, mismatched).totalSize();
	}

	private static BitSet bitmapOf(List<Long> ids) {
		BitSet bitmap = new BitSet();
		ids.forEach(id -> bitmap.set((int)(id - FIRST_ID)));
		return bitmap;
	}
}
