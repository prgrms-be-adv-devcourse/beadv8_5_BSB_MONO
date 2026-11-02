package com.bukang.member.app;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.SocialAccountAlreadyLinkedException;
import com.bukang.member.domain.exception.SocialLoginFailedException;
import com.bukang.member.domain.exception.SocialProviderAlreadyLinkedException;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.oauth2.LinkTicket;
import com.bukang.member.out.oauth2.PendingSignup;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialLoginStore;
import com.bukang.member.out.oauth2.SocialOAuth2Client;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;
import com.bukang.member.out.oauth2.StoredAuthorization;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인 시작과 콜백 (BE-031·032·033·036)
 * 결과는 브라우저를 보낼 프론트 경로로 돌려주고, 쿠키를 심는 일은 컨트롤러가 한다
 * 제공자와 주고받는 동안 DB 연결을 붙잡지 않도록 이 클래스에는 트랜잭션을 걸지 않는다. DB 작업은 각 저장소·유스케이스가 짧게 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginUseCase {
	// 계정 연결 결과로 그대로 화면에 알릴 오류 코드. 그 밖의 실패는 모두 AUTH_SOCIAL_FAILED로 알린다
	// 확인된 이메일이 이미 다른 회원 것이라 거절했다 (BE-032 결정 1). 화면 이동의 error 값으로만 쓴다
	static final String EMAIL_TAKEN_CODE = "AUTH_SOCIAL_EMAIL_TAKEN";
	private static final Set<String> LINK_ERROR_CODES = Set.of(
		SocialAccountAlreadyLinkedException.CODE, SocialProviderAlreadyLinkedException.CODE);

	private final SocialOAuth2Client socialOAuth2Client;
	private final SocialLoginStore socialLoginStore;
	private final SocialAccountRepository socialAccountRepository;
	private final SocialAccountUseCase socialAccountUseCase;
	private final TokenIssueUseCase tokenIssueUseCase;

	/**
	 * 인가 요청을 만들어 Redis에 10분 저장한다 (BE-031 결정 3)
	 * linkTicket(연결 표 쿠키 값)이 있으면 계정 연결이다. 표를 꺼내 지우고 회원 ID를 인가 요청에 함께 저장한다 (BE-032 결정 3)
	 * 키 설정을 먼저 확인해, 키가 없을 때 표만 소모되지 않게 한다
	 * requestId가 null이면 실패라서 쿠키를 심지 않고 location(실패 화면)으로 보낸다
	 */
	public AuthorizeResult begin(SocialProvider provider, String next, String linkTicket, HttpServletRequest request) {
		boolean link = linkTicket != null;
		String fallback = link ? SocialRedirects.ACCOUNTS_PATH : SocialRedirects.HOME_PATH;
		String safeNext = SocialRedirects.safeNextPath(next, fallback);
		String failurePath = link ? safeNext : SocialRedirects.LOGIN_PATH;

		if (!socialOAuth2Client.isConfigured(provider)) {
			log.warn("키가 설정되지 않은 소셜 로그인으로 시작했습니다. provider={}", provider);
			return AuthorizeResult.failed(failed(failurePath));
		}
		Long linkMemberId = null;
		if (link) {
			Optional<LinkTicket> ticket = socialLoginStore.takeLinkTicket(linkTicket);
			if (ticket.isEmpty() || ticket.get().provider() != provider) {
				return AuthorizeResult.failed(failed(failurePath));
			}
			linkMemberId = ticket.get().memberId();
		}

		AuthorizationStart start = socialOAuth2Client.begin(provider, request);
		StoredAuthorization started = start.authorization();
		String requestId = SocialRedirects.randomId();
		socialLoginStore.saveAuthorization(requestId, new StoredAuthorization(provider, started.state(),
			started.redirectUri(), started.scopes(), started.attributes(), safeNext, linkMemberId));
		return new AuthorizeResult(start.authorizationUri(), requestId);
	}

	/**
	 * 콜백을 처리한다. 쿠키의 요청 ID로 꺼낸 인가 요청의 state가 받은 state와 같을 때만 진행한다 (BE-031 결정 3)
	 * 인가 요청은 꺼내면서 지우므로, 같은 콜백 주소를 다시 열어도 진행되지 않는다
	 */
	public CallbackResult callback(SocialProvider provider, String requestId, String code, String state, String error) {
		Optional<StoredAuthorization> taken = requestId == null
			? Optional.empty()
			: socialLoginStore.takeAuthorization(requestId);
		if (taken.isEmpty()) {
			return new CallbackResult.Redirect(failed(SocialRedirects.LOGIN_PATH));
		}
		StoredAuthorization authorization = taken.get();
		String failurePath = authorization.isLink() ? authorization.next() : SocialRedirects.LOGIN_PATH;
		// error: 사용자가 동의 화면에서 취소했다. state가 다르면 다른 브라우저에서 시작한 콜백이다(로그인 위조 공격)
		if (error != null || code == null || state == null
			|| authorization.provider() != provider || !authorization.state().equals(state)) {
			return new CallbackResult.Redirect(failed(failurePath));
		}

		SocialIdentity identity;
		try {
			identity = socialOAuth2Client.complete(authorization, code, state);
		} catch (SocialLoginFailedException exception) {
			return new CallbackResult.Redirect(failed(failurePath));
		}
		return authorization.isLink() ? link(authorization, identity) : login(authorization, identity);
	}

	// 계정 연결: 결과를 next에 linked 또는 error로 붙인다 (BE-032 결정 5)
	private CallbackResult link(StoredAuthorization authorization, SocialIdentity identity) {
		try {
			socialAccountUseCase.link(authorization.linkMemberId(), identity);
		} catch (BusinessException exception) {
			String code = LINK_ERROR_CODES.contains(exception.getCode())
				? exception.getCode()
				: SocialLoginFailedException.CODE;
			return new CallbackResult.Redirect(SocialRedirects.withQuery(authorization.next(), "error", code));
		}
		return new CallbackResult.Redirect(
			SocialRedirects.withQuery(authorization.next(), "linked", identity.provider().getPathValue()));
	}

	// 로그인: 연결된 회원이 있으면 토큰 발급, 확인된 이메일이 다른 회원 것이면 거절, 아니면 가입 대기
	private CallbackResult login(StoredAuthorization authorization, SocialIdentity identity) {
		Optional<Long> memberId = socialAccountRepository.findActiveMemberId(identity.provider(),
			identity.providerUserId());
		if (memberId.isPresent()) {
			// 이메일 로그인과 같은 발급 코드라서 회전·재사용 탐지가 똑같이 동작한다 (BE-036)
			AuthTokens tokens = tokenIssueUseCase.issue(memberId.get());
			return new CallbackResult.LoggedIn(tokens,
				SocialRedirects.withQuery(SocialRedirects.COMPLETE_PATH, "next", authorization.next()));
		}

		if (identity.verifiedEmail() != null) {
			Optional<String> loginMethod = socialAccountUseCase.findLoginMethod(identity.verifiedEmail());
			if (loginMethod.isPresent()) {
				// 자동으로 연결하지 않는다. 화면은 「로그인한 뒤 계정 연결」을 안내한다 (BE-032 결정 1)
				String location = SocialRedirects.withQuery(SocialRedirects.LOGIN_PATH, "error", EMAIL_TAKEN_CODE);
				location = SocialRedirects.withQuery(location, "provider", identity.provider().getPathValue());
				location = SocialRedirects.withQuery(location, "joinedWith", loginMethod.get());
				return new CallbackResult.Redirect(location);
			}
		}

		String pendingId = SocialRedirects.randomId();
		socialLoginStore.savePendingSignup(pendingId, PendingSignup.from(identity));
		return new CallbackResult.SignupRequired(pendingId,
			SocialRedirects.withQuery(SocialRedirects.SIGNUP_PATH, "next", authorization.next()));
	}

	private static String failed(String path) {
		return SocialRedirects.withQuery(path, "error", SocialLoginFailedException.CODE);
	}

	/**
	 * @param location 브라우저를 보낼 주소. 성공이면 제공자 로그인 화면, 실패면 프론트 실패 화면
	 * @param requestId 인가 요청 쿠키에 심을 값. 실패면 null
	 */
	public record AuthorizeResult(String location, String requestId) {
		static AuthorizeResult failed(String location) {
			return new AuthorizeResult(location, null);
		}
	}

	public sealed interface CallbackResult {
		String location();

		// 연결된 회원으로 로그인했다. Refresh Token 쿠키를 심고 완료 화면으로 보낸다
		record LoggedIn(AuthTokens tokens, String location) implements CallbackResult {
		}

		// 처음 온 사람이다. 가입 대기 쿠키를 심고 소셜 가입 화면으로 보낸다
		record SignupRequired(String pendingId, String location) implements CallbackResult {
		}

		// 쿠키 없이 화면만 옮긴다 (실패, 이메일 거절, 연결 결과)
		record Redirect(String location) implements CallbackResult {
		}
	}
}
