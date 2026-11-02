package com.bukang.member.out.oauth2;

import com.bukang.member.domain.SocialProvider;

/**
 * 제공자 쪽 앱 연결을 끊는다 (BE-034). 탈퇴·연결 해제가 커밋된 뒤에 부른다
 * 실패해도 우리 쪽 처리는 되돌리지 않으므로, 구현은 예외를 던지지 않고 로그만 남긴다
 */
public interface SocialUnlinkPort {
	void unlink(SocialProvider provider, String providerUserId);
}
