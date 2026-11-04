package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartyTest {

	@Test
	@DisplayName("새 파티는 모집 중이고, 참가 링크 토큰은 URL에 그대로 쓸 수 있는 43자이며 파티마다 다르다")
	void create() {
		Party party = Party.create(7L, 3L, 1L);
		Party other = Party.create(7L, 4L, 1L);

		assertThat(party.getStatus()).isEqualTo(PartyStatus.RECRUITING);
		assertThat(party.getJoinToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(party.getJoinToken()).isNotEqualTo(other.getJoinToken());
	}
}
