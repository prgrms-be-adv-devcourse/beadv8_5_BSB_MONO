package com.bukang.member.out.kakao;

import java.util.List;

/**
 * 카카오 친구 목록·메시지·토큰 갱신 (kakao-crew-invite 결정). 사용자 토큰으로 부른다
 * 운영은 RestKakaoTalkClient, 테스트는 목으로 바꿔 카카오를 부르지 않는다
 * 카카오가 오류 코드로 답하면 KakaoApiException, 응답이 없거나 5xx면 KakaoUnavailableException(502)을 던진다
 */
public interface KakaoTalkClient {
	// 리프레시 토큰으로 액세스 토큰을 새로 받는다. 거절되면 KakaoApiException(-401)
	KakaoRefreshedToken refresh(String refreshToken);

	// 앱에 연결되고 친구 목록 제공에 동의한 카카오 친구 (최대 100명)
	List<KakaoFriend> friends(String accessToken);

	// 친구 한 명에게 기본 템플릿 메시지를 보낸다
	void send(String accessToken, String receiverUuid, String templateObjectJson);
}
