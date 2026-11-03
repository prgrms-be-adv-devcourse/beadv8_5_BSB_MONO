package com.bukang.member.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.bukang.member.out.mail.OutgoingMail;

class MemberMailConfigTest {
	private static final OutgoingMail MAIL = new OutgoingMail("runner1@crewrun.com", "제목", "본문", "<p>본문</p>");

	@Test
	@DisplayName("사용자 이름이 있으면 SMTP 인증을 그대로 켜 두고 그 주소로 보낸다")
	void keepsAuthWithUsername() {
		JavaMailSenderImpl sender = senderWithAuth();

		MemberMailConfig.smtpSender(sender, "crewrun@gmail.com", "크루런");

		assertThat(sender.getJavaMailProperties().getProperty(MemberMailConfig.SMTP_AUTH)).isEqualTo("true");
	}

	@Test
	@DisplayName("사용자 이름이 없으면 인증과 STARTTLS 없이 로컬 메일 서버(Mailpit과 같은 방식)로 보낸다")
	void sendsWithoutAuthWhenUsernameBlank() throws Exception {
		try (ServerSocket server = new ServerSocket(0)) {
			CompletableFuture<List<String>> received = CompletableFuture.supplyAsync(() -> acceptOne(server));
			JavaMailSenderImpl sender = senderWithAuth();
			sender.setHost("localhost");
			sender.setPort(server.getLocalPort());
			sender.setUsername("");
			sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "false");

			MemberMailConfig.smtpSender(sender, "", "크루런").send(MAIL);

			List<String> commands = received.get(10, TimeUnit.SECONDS);
			assertThat(commands).noneMatch(line -> line.startsWith("AUTH") || line.startsWith("STARTTLS"));
			assertThat(commands).anyMatch(line -> line.contains(MemberMailConfig.NO_AUTH_FROM_ADDRESS));
			assertThat(commands).anyMatch(line -> line.contains("runner1@crewrun.com"));
		}
	}

	// Gmail 설정처럼 인증을 켠 채로 시작한다
	private static JavaMailSenderImpl senderWithAuth() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.getJavaMailProperties().setProperty(MemberMailConfig.SMTP_AUTH, "true");
		sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
		sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
		return sender;
	}

	// 인증·TLS를 알리지 않는 최소 SMTP 서버. 받은 명령 줄을 돌려준다
	private static List<String> acceptOne(ServerSocket server) {
		List<String> commands = new ArrayList<>();
		try (Socket socket = server.accept();
			BufferedReader in = new BufferedReader(
				new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
			out.print("220 localhost\r\n");
			out.flush();
			boolean data = false;
			String line;
			while ((line = in.readLine()) != null) {
				if (data) {
					if (".".equals(line)) {
						data = false;
						reply(out, "250 OK");
					}
					continue;
				}
				commands.add(line);
				String command = line.toUpperCase();
				if (command.startsWith("EHLO")) {
					reply(out, "250 localhost");
				} else if (command.startsWith("DATA")) {
					data = true;
					reply(out, "354 Start mail input");
				} else if (command.startsWith("QUIT")) {
					reply(out, "221 Bye");
					break;
				} else {
					reply(out, "250 OK");
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return commands;
	}

	private static void reply(PrintWriter out, String message) {
		out.print(message + "\r\n");
		out.flush();
	}
}
