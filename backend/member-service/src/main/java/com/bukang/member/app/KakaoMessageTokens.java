package com.bukang.member.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.domain.SocialAccount;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.KakaoMessageConsentRequiredException;
import com.bukang.member.domain.exception.KakaoNotLinkedException;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.kakao.KakaoApiException;
import com.bukang.member.out.kakao.KakaoRefreshedToken;
import com.bukang.member.out.kakao.KakaoTalkClient;
import com.bukang.member.out.oauth2.LinkTicket;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialLoginStore;
import com.bukang.member.out.oauth2.SocialTokens;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 메시지 추가 동의와 카카오 사용자 토큰 저장·갱신·삭제 (kakao-crew-invite 결정 1·2)
 * 토큰은 그 회원의 카카오 소셜 연결 행에 암호문으로 둔다. 연결 해제·탈퇴로 행이 지워지면 토큰도 사라진다
 * 토큰을 읽거나 바꿀 때는 그 행을 잠가, 두 요청이 같이 갱신해 버려진 리프레시 토큰을 저장하지 않게 한다
 * 토큰 값은 로그에 남기지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoMessageTokens {
	public static final Set<String> MESSAGE_SCOPES = Set.of("talk_message", "friends");
	public static final String ACCOUNT_MISMATCH_CODE = "KAKAO_ACCOUNT_MISMATCH";
	public static final String CONSENT_DENIED_CODE = "KAKAO_CONSENT_DENIED";
	// 이 시간 안에 끝나는 액세스 토큰은 미리 갱신한다. 카카오를 부르는 동안 끝나지 않게 한다
	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

	private final SocialAccountRepository socialAccountRepository;
	private final SocialLoginStore socialLoginStore;
	private final KakaoTalkClient kakaoTalkClient;
	private final Clock clock;

	// 메시지 동의 표. 계정 연결 표와 같은 쿠키·시작 주소를 쓰고, 표에 동의 목적을 적는다 (결정 2-3·4)
	@Transactional(readOnly = true)
	public String issueConsentTicket(long memberId) {
		if (!socialAccountRepository.existsByMemberIdAndProvider(memberId, SocialProvider.KAKAO)) {
			throw new KakaoNotLinkedException();
		}
		String ticket = SocialRedirects.randomId();
		socialLoginStore.saveLinkTicket(ticket, new LinkTicket(memberId, SocialProvider.KAKAO, true));
		return ticket;
	}

	/**
	 * 동의 콜백에서 받은 토큰을 저장한다. 실패하면 화면에 알릴 오류 코드를 돌려준다 (결정 2-5·6)
	 * 다른 카카오 계정으로 동의했으면 저장하지 않는다. 남의 이름으로 메시지가 나가지 않게 하기 위해서다
	 */
	@Transactional
	public Optional<String> saveConsent(long memberId, SocialIdentity identity) {
		Optional<SocialAccount> found = socialAccountRepository.findByMemberIdAndProviderForUpdate(memberId,
			SocialProvider.KAKAO);
		if (found.isEmpty() || !found.get().getProviderUserId().equals(identity.providerUserId())) {
			return Optional.of(ACCOUNT_MISMATCH_CODE);
		}
		SocialTokens tokens = identity.tokens();
		if (tokens == null || tokens.accessToken() == null || tokens.scopes() == null
			|| !tokens.scopes().containsAll(MESSAGE_SCOPES)) {
			return Optional.of(CONSENT_DENIED_CODE);
		}
		Instant expiresAt = tokens.accessTokenExpiresAt() == null ? clock.instant() : tokens.accessTokenExpiresAt();
		found.get().saveTokens(tokens.accessToken(), toLocal(expiresAt), tokens.refreshToken(), tokens.scopes());
		return Optional.empty();
	}

	/**
	 * 카카오를 부를 액세스 토큰. 곧 끝나면 리프레시 토큰으로 갱신해 저장한다 (결정 1-4·5)
	 * 토큰이 없거나 범위가 모자라거나 갱신이 거절되면 토큰을 지우고 KakaoMessageConsentRequiredException을 던진다
	 * 지운 것은 커밋해야 하므로 이 예외로는 되돌리지 않는다
	 */
	@Transactional(noRollbackFor = KakaoMessageConsentRequiredException.class)
	public String accessToken(long memberId) {
		SocialAccount account = socialAccountRepository.findByMemberIdAndProviderForUpdate(memberId,
			SocialProvider.KAKAO).orElseThrow(KakaoNotLinkedException::new);
		if (!account.hasScopes(MESSAGE_SCOPES)) {
			throw new KakaoMessageConsentRequiredException();
		}
		LocalDateTime now = toLocal(clock.instant());
		if (account.getAccessTokenExpiresAt().isAfter(now.plus(REFRESH_MARGIN))) {
			return account.getAccessToken();
		}
		if (account.getRefreshToken() == null) {
			account.clearTokens();
			throw new KakaoMessageConsentRequiredException();
		}
		KakaoRefreshedToken refreshed;
		try {
			refreshed = kakaoTalkClient.refresh(account.getRefreshToken());
		} catch (KakaoApiException exception) {
			log.info("카카오 토큰 갱신이 거절돼 저장한 토큰을 지웁니다. memberId={}", memberId);
			account.clearTokens();
			throw new KakaoMessageConsentRequiredException();
		}
		account.refreshTokens(refreshed.accessToken(), now.plusSeconds(refreshed.expiresIn()),
			refreshed.refreshToken());
		return refreshed.accessToken();
	}

	// 카카오가 토큰을 받지 않았다(-401·-402). 저장한 토큰을 지워 다음에 다시 동의하게 한다 (결정 1-6)
	@Transactional
	public void discard(long memberId) {
		socialAccountRepository.findByMemberIdAndProviderForUpdate(memberId, SocialProvider.KAKAO)
			.ifPresent(SocialAccount::clearTokens);
	}

	private static LocalDateTime toLocal(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}
}
