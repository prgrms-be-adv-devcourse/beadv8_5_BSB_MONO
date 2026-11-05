package com.bukang.member.out.kakao;

/**
 * 토큰 갱신 결과
 *
 * @param expiresIn 액세스 토큰이 끝나기까지 남은 초
 * @param refreshToken 카카오가 리프레시 토큰을 바꿔 줄 때만 있다. 없으면 null이고 기존 값을 계속 쓴다
 */
public record KakaoRefreshedToken(String accessToken, long expiresIn, String refreshToken) {
	@Override
	public String toString() {
		return "KakaoRefreshedToken[expiresIn=" + expiresIn + "]";
	}
}
