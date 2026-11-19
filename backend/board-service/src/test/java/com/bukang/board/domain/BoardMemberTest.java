package com.bukang.board.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardMemberTest {
	private static final LocalDateTime T = LocalDateTime.of(2026, 11, 1, 12, 0);

	@Test
	@DisplayName("저장된 수정 시각보다 옛 메시지면 저장된 쪽이 새 것이다")
	void storedIsNewerThanOlderMessage() {
		BoardMember member = new BoardMember(1L, "러너", T, T);

		assertThat(member.isNewerThan(T.minusSeconds(1))).isTrue();
	}

	@Test
	@DisplayName("같은 시각이거나 더 새 메시지면 저장된 쪽이 새 것이 아니다")
	void storedIsNotNewerThanSameOrNewerMessage() {
		BoardMember member = new BoardMember(1L, "러너", T, T);

		assertThat(member.isNewerThan(T)).isFalse();
		assertThat(member.isNewerThan(T.plusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("프로필을 아직 못 받은 복사본은 어떤 메시지보다도 새 것이 아니다")
	void profileNotSyncedIsNeverNewer() {
		BoardMember member = BoardMember.sellerOf(1L, true, T);

		assertThat(member.isNewerThan(T.minusDays(1))).isFalse();
	}
}
