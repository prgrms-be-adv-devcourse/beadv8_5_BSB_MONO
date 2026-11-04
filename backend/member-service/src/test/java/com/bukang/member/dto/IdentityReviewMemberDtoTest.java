package com.bukang.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 어드민 「확인이 필요한 계정」 응답의 개인정보 가리기 (BE-038 결정 4)
class IdentityReviewMemberDtoTest {
	@Test
	@DisplayName("이름은 첫 글자와 끝 글자만 남기고, 두 글자면 끝 글자를, 한 글자면 전부 가린다")
	void masksName() {
		assertThat(IdentityReviewMemberDto.maskName("홍길동")).isEqualTo("홍*동");
		assertThat(IdentityReviewMemberDto.maskName("남궁민수")).isEqualTo("남**수");
		assertThat(IdentityReviewMemberDto.maskName("김수")).isEqualTo("김*");
		assertThat(IdentityReviewMemberDto.maskName("김")).isEqualTo("*");
	}

	@Test
	@DisplayName("휴대폰은 하이픈 유무와 상관없이 앞 3자리와 뒤 4자리만 남긴다")
	void masksPhone() {
		assertThat(IdentityReviewMemberDto.maskPhone("010-1234-5678")).isEqualTo("010-****-5678");
		assertThat(IdentityReviewMemberDto.maskPhone("01012345678")).isEqualTo("010-****-5678");
		assertThat(IdentityReviewMemberDto.maskPhone("010-123-5678")).isEqualTo("010-****-5678");
	}

	@Test
	@DisplayName("이메일은 아이디 앞 2글자와 도메인만 남긴다")
	void masksEmail() {
		assertThat(IdentityReviewMemberDto.maskEmail("runner@crewrun.com")).isEqualTo("ru***@crewrun.com");
		assertThat(IdentityReviewMemberDto.maskEmail("a@crewrun.com")).isEqualTo("a***@crewrun.com");
	}
}
