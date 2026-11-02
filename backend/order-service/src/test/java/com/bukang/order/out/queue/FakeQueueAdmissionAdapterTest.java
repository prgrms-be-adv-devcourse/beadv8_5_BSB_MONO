package com.bukang.order.out.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeQueueAdmissionAdapterTest {
	private static final long MEMBER_ID = 1;
	private static final long RACE_ID = 5;
	private static final long COURSE_ID = 50;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 11, 0);

	private final FakeQueueAdmissionAdapter adapter = new FakeQueueAdmissionAdapter(10);

	@Test
	@DisplayName("처음 물을 때 입장해 정한 시간 뒤에 끝나고, 다시 물어도 같은 끝 시각을 돌려준다 (새로고침해도 타이머가 같다)")
	void remembersAdmission() {
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW)).contains(NOW.plusMinutes(10));
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW.plusMinutes(3)))
			.contains(NOW.plusMinutes(10));
	}

	@Test
	@DisplayName("끝 시각부터는 입장 중이 아니고, 다시 입장하면 지금부터 다시 센다")
	void expiresAndReenters() {
		adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW);

		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW.plusMinutes(10))).isEmpty();
		LocalDateTime later = NOW.plusMinutes(20);
		assertThat(adapter.enter(MEMBER_ID, COURSE_ID, later)).isEqualTo(later.plusMinutes(10));
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, later)).contains(later.plusMinutes(10));
	}

	@Test
	@DisplayName("끝난 입장은 [결제하기]에서는 그대로 끝났고, 결제 페이지를 다시 열면 다시 입장한다 (운영에도 다시 살 길이 있다)")
	void reentersOnPageOpen() {
		assertThat(adapter.findAdmissionEndsAtOnPageOpen(MEMBER_ID, RACE_ID, COURSE_ID, NOW))
			.contains(NOW.plusMinutes(10));
		assertThat(adapter.findAdmissionEndsAtOnPageOpen(MEMBER_ID, RACE_ID, COURSE_ID, NOW.plusMinutes(3)))
			.contains(NOW.plusMinutes(10));

		LocalDateTime later = NOW.plusMinutes(12);
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, later)).isEmpty();
		assertThat(adapter.findAdmissionEndsAtOnPageOpen(MEMBER_ID, RACE_ID, COURSE_ID, later))
			.contains(later.plusMinutes(10));
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, later)).contains(later.plusMinutes(10));
	}

	@Test
	@DisplayName("입장 시간 설정은 1~15분만 받는다 (15분보다 길면 결제 기한과 타이머가 어긋난다)")
	void rejectsInvalidMinutes() {
		assertThatThrownBy(() -> new FakeQueueAdmissionAdapter(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FakeQueueAdmissionAdapter(16)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new FakeQueueAdmissionAdapter(15).findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW))
			.contains(NOW.plusMinutes(15));
	}

	@Test
	@DisplayName("입장은 회원·종목마다 따로 센다")
	void separatePerMemberAndCourse() {
		adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW);

		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID + 1, RACE_ID, COURSE_ID, NOW.plusMinutes(5)))
			.contains(NOW.plusMinutes(15));
		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID + 1, NOW.plusMinutes(5)))
			.contains(NOW.plusMinutes(15));
	}

	@Test
	@DisplayName("입장 시간을 설정으로 줄일 수 있다 (만료를 빨리 확인)")
	void configurableMinutes() {
		FakeQueueAdmissionAdapter shortAdapter = new FakeQueueAdmissionAdapter(1);

		assertThat(shortAdapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW)).contains(NOW.plusMinutes(1));
	}

	@Test
	@DisplayName("기록이 많아지면 끝난 지 오래된 기록만 지우고, 막 끝난 기록은 남겨 [결제하기]가 계속 403이다")
	void prunesOldRecords() {
		FakeQueueAdmissionAdapter small = new FakeQueueAdmissionAdapter(10, 3, 100);
		small.findAdmissionEndsAt(1, RACE_ID, COURSE_ID, NOW);
		small.findAdmissionEndsAt(2, RACE_ID, COURSE_ID, NOW);
		LocalDateTime later = NOW.plusMinutes(10 + FakeQueueAdmissionAdapter.RETENTION_MINUTES + 1);
		small.findAdmissionEndsAt(3, RACE_ID, COURSE_ID, later.minusMinutes(15));
		assertThat(small.size()).isEqualTo(3);

		// 4번째 기록을 넣을 때 정리한다: 회원 1·2는 끝난 지 오래돼 지우고, 회원 3은 막 끝나 남긴다
		small.findAdmissionEndsAt(4, RACE_ID, COURSE_ID, later);

		assertThat(small.size()).isEqualTo(2);
		assertThat(small.findAdmissionEndsAt(3, RACE_ID, COURSE_ID, later)).isEmpty();
	}

	@Test
	@DisplayName("그래도 상한을 넘으면 끝난 기록을 모두 지우고, 입장 중인 기록은 남긴다")
	void prunesEndedRecordsAtLimit() {
		FakeQueueAdmissionAdapter small = new FakeQueueAdmissionAdapter(10, 2, 3);
		small.findAdmissionEndsAt(1, RACE_ID, COURSE_ID, NOW);
		small.findAdmissionEndsAt(2, RACE_ID, COURSE_ID, NOW);
		LocalDateTime later = NOW.plusMinutes(11);
		small.findAdmissionEndsAt(3, RACE_ID, COURSE_ID, later);
		assertThat(small.size()).isEqualTo(3);

		small.findAdmissionEndsAt(4, RACE_ID, COURSE_ID, later);

		// 회원 1·2(끝남)는 지우고 3(입장 중)·4(새 입장)만 남는다
		assertThat(small.size()).isEqualTo(2);
		assertThat(small.findAdmissionEndsAt(3, RACE_ID, COURSE_ID, later)).contains(later.plusMinutes(10));
	}

	@Test
	@DisplayName("기록이 적으면 끝난 지 오래돼도 지우지 않는다 (매번 전체를 훑지 않는다)")
	void keepsRecordsWhenSmall() {
		adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, COURSE_ID, NOW);

		adapter.findAdmissionEndsAt(MEMBER_ID + 1, RACE_ID, COURSE_ID, NOW.plusDays(1));

		assertThat(adapter.size()).isEqualTo(2);
	}
}
