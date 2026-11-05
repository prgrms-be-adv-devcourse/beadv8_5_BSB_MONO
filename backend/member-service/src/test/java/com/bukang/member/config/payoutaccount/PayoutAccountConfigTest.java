package com.bukang.member.config.payoutaccount;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.out.payoutaccount.FakePayoutAccountVerifier;
import com.bukang.member.out.payoutaccount.OpenBankingAccountVerifier;
import com.bukang.member.out.payoutaccount.PayoutAccountInquiry;
import com.bukang.member.out.payoutaccount.PayoutAccountVerifier;

// 키 유무로 계좌 확인 구현을 고른다 (BUK-50)
class PayoutAccountConfigTest {
	private static final PayoutAccountInquiry INQUIRY = new PayoutAccountInquiry(
		"004", "1101230000678", PayoutAccountHolderType.PERSONAL, "950412", "김러너");

	@Test
	@DisplayName("키가 없으면 운영에서도 가짜 확인이 신청자 이름으로 통과시킨다 (오픈뱅킹 이용 승인 거절로 목업 사용)")
	void withoutKeysUsesFake() {
		PayoutAccountVerifier verifier = PayoutAccountConfig.create(properties("", ""), Clock.systemUTC());

		assertThat(verifier).isInstanceOf(FakePayoutAccountVerifier.class);
		assertThat(verifier.verify(INQUIRY)).isEqualTo("김러너");
	}

	@Test
	@DisplayName("키가 있으면 오픈뱅킹으로 확인한다")
	void withKeysUsesOpenBanking() {
		assertThat(PayoutAccountConfig.create(properties("id", "secret"), Clock.systemUTC()))
			.isInstanceOf(OpenBankingAccountVerifier.class);
	}

	private static PayoutAccountProperties properties(String clientId, String clientSecret) {
		return new PayoutAccountProperties(5, Duration.ofHours(24), new PayoutAccountProperties.OpenBanking(
			"https://testapi.openbanking.or.kr", clientId, clientSecret, "M202600001", " ", "6", Duration.ofSeconds(3)));
	}
}
