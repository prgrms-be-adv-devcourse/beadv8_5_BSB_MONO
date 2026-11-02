package com.bukang.member.app;

import org.springframework.dao.DataIntegrityViolationException;

import com.bukang.member.domain.exception.DuplicateEmailException;
import com.bukang.member.domain.exception.DuplicateIdentityException;
import com.bukang.member.domain.exception.DuplicateNicknameException;
import com.bukang.member.domain.exception.DuplicatePhoneException;

/**
 * 회원 가입·수정이 함께 쓰는 중복 예외 메시지와, DB 유니크 제약 위반을 중복 예외(409)로 바꾸는 방법 (BE-014)
 * 예외 메시지에는 입력값(개인정보)을 넣지 않는다 (응답과 로그에 평문이 남지 않도록)
 */
final class MemberDuplicateExceptions {
	static final String DUPLICATE_EMAIL_MESSAGE = "이미 사용 중인 이메일입니다.";
	static final String DUPLICATE_NICKNAME_MESSAGE = "이미 사용 중인 닉네임입니다.";
	static final String DUPLICATE_PHONE_MESSAGE = "이미 사용 중인 전화번호입니다.";
	static final String DUPLICATE_IDENTITY_MESSAGE = "이미 가입한 사람입니다. 로그인하거나 계정 연결에서 연결해주세요.";
	// Member의 @UniqueConstraint 이름과 같아야 한다
	private static final String EMAIL_CONSTRAINT = "uk_member_email";
	private static final String NICKNAME_CONSTRAINT = "uk_member_nickname";
	private static final String PHONE_CONSTRAINT = "uk_member_phone_hash";
	private static final String IDENTITY_CONSTRAINT = "uk_member_identity_hash";

	private MemberDuplicateExceptions() {
	}

	// 제약 이름으로 어느 칸이 겹쳤는지 구별한다. 회원 제약이 아니면 원래 예외를 그대로 던진다
	static RuntimeException from(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		if (message == null) {
			return exception;
		}
		String lowerCaseMessage = message.toLowerCase();
		// 같은 사람(이름 + 생년월일 + 휴대폰 번호)이 동시에 두 번 가입하면 늦은 쪽이 이 제약에 걸린다
		if (lowerCaseMessage.contains(IDENTITY_CONSTRAINT)) {
			return new DuplicateIdentityException(DUPLICATE_IDENTITY_MESSAGE);
		}
		if (lowerCaseMessage.contains(NICKNAME_CONSTRAINT)) {
			return new DuplicateNicknameException(DUPLICATE_NICKNAME_MESSAGE);
		}
		if (lowerCaseMessage.contains(PHONE_CONSTRAINT)) {
			return new DuplicatePhoneException(DUPLICATE_PHONE_MESSAGE);
		}
		if (lowerCaseMessage.contains(EMAIL_CONSTRAINT)) {
			return new DuplicateEmailException(DUPLICATE_EMAIL_MESSAGE);
		}
		return exception;
	}
}
