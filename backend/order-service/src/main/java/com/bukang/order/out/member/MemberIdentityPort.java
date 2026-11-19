package com.bukang.order.out.member;

import java.time.LocalDate;

import com.bukang.order.domain.exception.IdentityVerificationRequiredException;

/**
 * 참가 신청서에 넣을 회원의 본인인증 값(이름·생년월일·휴대폰)을 회원 서비스에서 가져온다 (11/05 결정 ⑧, CRU-59)
 * 읽기만 하므로 몇 번 불러도 된다. 연결하지 못하면 ExternalServiceUnavailableException(503)을 던진다
 * 본인인증을 하지 않은 회원은 값이 null인 칸이 있다
 */
public interface MemberIdentityPort {
	MemberIdentity find(long memberId);

	// 신청서에 넣을 값이 다 있는 회원만 돌려주고, 빈 칸이 있으면 본인인증부터 하라고 409로 막는다
	default MemberIdentity findVerified(long memberId) {
		MemberIdentity identity = find(memberId);
		if (!identity.isComplete()) {
			throw new IdentityVerificationRequiredException();
		}
		return identity;
	}

	record MemberIdentity(String name, LocalDate birth, String phone) {
		// 휴대폰은 신청서 형식(010-1234-5678)으로 맞출 수 있는 값이어야 한다
		boolean isComplete() {
			return name != null && !name.isBlank() && birth != null
				&& phone != null && phone.matches("^01\\d-?\\d{3,4}-?\\d{4}$");
		}
	}
}
