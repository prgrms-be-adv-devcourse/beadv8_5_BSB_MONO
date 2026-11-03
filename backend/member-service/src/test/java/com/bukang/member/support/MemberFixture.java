package com.bukang.member.support;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.Gender;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

/**
 * 테스트 Given용 기본값. 테스트는 자기가 확인하는 값만 바꾼다
 * 예: memberFacade.join(MemberFixture.joinRequest().nickname("러너2").build(identityStore))
 * 이메일 · 닉네임 · 휴대폰 번호는 부를 때마다 달라, 한 테스트에서 여러 명을 가입시켜도 중복 검사에 걸리지 않는다
 * 본인인증 해시는 휴대폰 번호로 만들어지므로 같은 사람 검사에도 걸리지 않는다
 */
public final class MemberFixture {
	private static final AtomicLong SEQUENCE = new AtomicLong();

	private MemberFixture() {
	}

	public static JoinRequestBuilder joinRequest() {
		return new JoinRequestBuilder(SEQUENCE.incrementAndGet());
	}

	// 본인 명의 계좌라, 가입 이름(김러너)과 같은 예금주를 돌려주는 테스트 확인을 통과한다
	public static SellerApplyRequestDto sellerApplyRequest() {
		return new SellerApplyRequestDto(SellerType.BUSINESS, "크루런 스포츠", "123-45-67890", "김러너",
			"004", "1101230000678", PayoutAccountHolderType.PERSONAL);
	}

	public static final class JoinRequestBuilder {
		private String email;
		private String password = "password1234";
		private String name = "김러너";
		private String nickname;
		private String phone;
		private LocalDate birthDate = LocalDate.of(1995, 4, 12);
		private Gender gender;
		private List<TermsType> agreedTerms = List.of(TermsType.SERVICE, TermsType.PRIVACY);

		// 테스트가 직접 쓰는 010-1xxx · 010-2xxx 번호와 겹치지 않게 010-9로 시작한다
		private JoinRequestBuilder(long sequence) {
			String digits = "%07d".formatted(sequence);
			this.email = "fixture" + sequence + "@crewrun.com";
			this.nickname = "픽스처" + sequence;
			this.phone = "010-9" + digits.substring(0, 3) + "-" + digits.substring(3);
		}

		public JoinRequestBuilder email(String email) {
			this.email = email;
			return this;
		}

		public JoinRequestBuilder password(String password) {
			this.password = password;
			return this;
		}

		public JoinRequestBuilder name(String name) {
			this.name = name;
			return this;
		}

		public JoinRequestBuilder nickname(String nickname) {
			this.nickname = nickname;
			return this;
		}

		public JoinRequestBuilder phone(String phone) {
			this.phone = phone;
			return this;
		}

		public JoinRequestBuilder birthDate(LocalDate birthDate) {
			this.birthDate = birthDate;
			return this;
		}

		public JoinRequestBuilder gender(Gender gender) {
			this.gender = gender;
			return this;
		}

		public JoinRequestBuilder agreedTerms(List<TermsType> agreedTerms) {
			this.agreedTerms = agreedTerms;
			return this;
		}

		// 본인인증을 마친 것처럼 저장소에 넣고, 받은 토큰을 담은 가입 요청을 만든다
		public MemberJoinRequestDto build(InMemoryIdentityVerificationStore identityStore) {
			return identityStore.joinRequest(email, password, name, nickname, phone, birthDate, gender, agreedTerms);
		}
	}
}
