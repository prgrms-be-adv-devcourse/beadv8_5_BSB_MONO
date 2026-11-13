package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.inquiry.out.member.FakeMemberAdminAdapter;
import com.bukang.inquiry.out.race.FakeRaceSellerAdapter;

class FakeRaceSellerAdapterTest {
	@Test
	@DisplayName("가짜 상품 서비스: 대회 9999는 없는 대회이고, 그 밖의 대회는 판매자 회원 1번이다")
	void fakeRule() {
		FakeRaceSellerAdapter adapter = new FakeRaceSellerAdapter();

		assertThat(adapter.findSellerMemberId(9999L)).isEmpty();
		assertThat(adapter.findSellerMemberId(1L)).contains(1L);
		assertThat(adapter.findSellerMemberId(123L)).contains(1L);
	}

	@Test
	@DisplayName("가짜 회원 서비스: 누구나 관리자다 (개발 전용)")
	void fakeAdmin() {
		assertThat(new FakeMemberAdminAdapter().isAdmin(5L)).isTrue();
	}
}
