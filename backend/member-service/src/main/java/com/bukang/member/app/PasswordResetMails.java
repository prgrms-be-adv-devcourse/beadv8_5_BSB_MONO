package com.bukang.member.app;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.util.HtmlUtils;

import com.bukang.member.domain.SocialProvider;
import com.bukang.member.out.mail.OutgoingMail;

/**
 * 비밀번호 재설정 메일 문구 (BUK-40). 문구는 이 클래스에서만 고친다
 * 이미지와 외부 리소스는 넣지 않는다(스팸 점수). 회원 이름도 넣지 않아 HTML에 사용자 입력이 들어가지 않는다
 */
final class PasswordResetMails {
	private PasswordResetMails() {
	}

	static OutgoingMail resetLink(String to, String link, Duration ttl) {
		String minutes = String.valueOf(ttl.toMinutes());
		String text = """
			비밀번호 재설정을 요청하셨어요.

			아래 링크에서 새 비밀번호를 정해 주세요.
			%s

			링크는 %s분 동안 한 번만 쓸 수 있어요.
			요청하지 않으셨다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.
			""".formatted(link, minutes);
		String body = """
			<p>비밀번호 재설정을 요청하셨어요.</p>
			<p>%s</p>
			<p>링크는 %s분 동안 한 번만 쓸 수 있어요.<br>
			요청하지 않으셨다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.</p>
			""".formatted(button(link, "비밀번호 재설정하기"), minutes);
		return new OutgoingMail(to, "[크루런] 비밀번호 재설정 링크", text, html(body));
	}

	static OutgoingMail passwordChanged(String to, String resetPageLink) {
		String text = """
			크루런 비밀번호가 바뀌었어요.
			다른 기기의 로그인도 모두 풀렸어요.

			본인이 바꾸지 않았다면 아래 링크에서 비밀번호 재설정을 다시 해 주세요.
			%s
			""".formatted(resetPageLink);
		String body = """
			<p>크루런 비밀번호가 바뀌었어요.<br>
			다른 기기의 로그인도 모두 풀렸어요.</p>
			<p>본인이 바꾸지 않았다면 비밀번호 재설정을 다시 해 주세요.</p>
			<p>%s</p>
			""".formatted(button(resetPageLink, "비밀번호 재설정하기"));
		return new OutgoingMail(to, "[크루런] 비밀번호가 바뀌었어요", text, html(body));
	}

	// 비밀번호가 없는 소셜 가입 회원. 재설정 링크 대신 가입한 제공자로 로그인하라고 알린다
	static OutgoingMail socialAccount(String to, List<SocialProvider> providers, String loginLink) {
		String names = providers.isEmpty()
			? "소셜 로그인"
			: providers.stream().map(SocialProvider::getDisplayName).collect(Collectors.joining(" 또는 "));
		String text = """
			비밀번호 재설정을 요청하셨어요.

			이 이메일은 %s로 가입한 계정이라 비밀번호가 없어요.
			아래 로그인 화면에서 %s로 로그인해 주세요.
			%s

			요청하지 않으셨다면 이 메일을 무시해 주세요.
			""".formatted(names, names, loginLink);
		String escapedNames = HtmlUtils.htmlEscape(names);
		String body = """
			<p>비밀번호 재설정을 요청하셨어요.</p>
			<p>이 이메일은 %s로 가입한 계정이라 비밀번호가 없어요.<br>
			%s로 로그인해 주세요.</p>
			<p>%s</p>
			<p>요청하지 않으셨다면 이 메일을 무시해 주세요.</p>
			""".formatted(escapedNames, escapedNames, button(loginLink, "로그인하러 가기"));
		return new OutgoingMail(to, "[크루런] 로그인 방법 안내", text, html(body));
	}

	private static String button(String link, String label) {
		String href = HtmlUtils.htmlEscape(link);
		return "<a href=\"" + href + "\" style=\"display:inline-block;padding:12px 20px;background:#1a73e8;"
			+ "color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold\">" + label + "</a>";
	}

	private static String html(String body) {
		return """
			<!doctype html>
			<html lang="ko">
			<body style="margin:0;padding:24px;font-family:sans-serif;font-size:15px;line-height:1.6;color:#222222">
			<p style="font-size:18px;font-weight:bold">크루런</p>
			%s
			</body>
			</html>
			""".formatted(body);
	}
}
