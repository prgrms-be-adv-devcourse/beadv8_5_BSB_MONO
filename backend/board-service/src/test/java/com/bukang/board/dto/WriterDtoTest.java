package com.bukang.board.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.board.domain.BoardMember;

class WriterDtoTest {

	@Test
	@DisplayName("판매자 이벤트로 먼저 만든 복사본은 닉네임을 「알 수 없는 회원」으로 보여 준다")
	void sellerOnlyReplicaShowsUnknownNickname() {
		BoardMember member = BoardMember.sellerOf(1L, true, LocalDateTime.of(2026, 11, 10, 12, 0));

		WriterDto writer = WriterDto.of(member);

		assertThat(writer.nickname()).isEqualTo("알 수 없는 회원");
		assertThat(writer.seller()).isTrue();
	}
}
