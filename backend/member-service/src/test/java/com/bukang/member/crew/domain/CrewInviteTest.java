package com.bukang.member.crew.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.member.crew.domain.CrewInvite.UnusableReason;

// 초대를 쓸 수 없는 이유 (CrewInvite.unusableReason): 쓰임 → 취소 → 만료 순서로 본다
class CrewInviteTest {
	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 18, 10, 0);

	@Test
	@DisplayName("쓸 수 있는 초대는 이유가 없다")
	void usable() {
		assertThat(invite().unusableReason(CREATED.plusHours(23))).isEmpty();
	}

	@Test
	@DisplayName("만료 시각과 같거나 지나면 만료다")
	void expired() {
		CrewInvite invite = invite();

		assertThat(invite.unusableReason(invite.getExpiresAt().minusNanos(1))).isEmpty();
		assertThat(invite.unusableReason(invite.getExpiresAt())).contains(UnusableReason.EXPIRED);
		assertThat(invite.unusableReason(invite.getExpiresAt().plusDays(1))).contains(UnusableReason.EXPIRED);
	}

	@Test
	@DisplayName("취소한 초대는 만료 전이어도 취소다")
	void canceled() {
		CrewInvite invite = invite();
		ReflectionTestUtils.setField(invite, "canceledAt", CREATED.plusHours(1));

		assertThat(invite.unusableReason(CREATED.plusHours(2))).contains(UnusableReason.CANCELED);
	}

	@Test
	@DisplayName("취소한 뒤 만료 시각이 지나도 취소가 만료보다 먼저다")
	void canceledBeforeExpired() {
		CrewInvite invite = invite();
		ReflectionTestUtils.setField(invite, "canceledAt", CREATED.plusHours(1));

		assertThat(invite.unusableReason(CREATED.plusDays(2))).contains(UnusableReason.CANCELED);
	}

	@Test
	@DisplayName("옛 방식으로 쓰인 초대는 취소·만료와 겹쳐도 쓰임이 가장 먼저다")
	void usedFirst() {
		CrewInvite invite = invite();
		ReflectionTestUtils.setField(invite, "usedAt", CREATED.plusHours(1));
		ReflectionTestUtils.setField(invite, "canceledAt", CREATED.plusHours(2));

		assertThat(invite.unusableReason(CREATED.plusDays(2))).contains(UnusableReason.USED);
	}

	private CrewInvite invite() {
		return CrewInvite.create(1L, "token", "hash", 10L, CREATED);
	}
}
