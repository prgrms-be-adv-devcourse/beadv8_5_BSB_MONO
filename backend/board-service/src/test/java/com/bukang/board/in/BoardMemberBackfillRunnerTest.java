package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import com.bukang.board.out.member.MemberReplicaPort;

/**
 * 게시판이 다 뜬 뒤 뒤에서 채우기를 시작하는지, 그리고 test 프로파일에서는 자동으로 돌지 않는지 확인한다
 */
class BoardMemberBackfillRunnerTest {

	@Nested
	@SpringBootTest
	@ActiveProfiles("test")
	@DisplayName("test 프로파일 기본값")
	class DisabledInTest {
		@Autowired
		private ApplicationContext context;

		@Test
		@DisplayName("자동으로 채우지 않는다 (러너 빈이 없다)")
		void runnerAbsent() {
			assertThat(context.getBeanNamesForType(BoardMemberBackfillRunner.class)).isEmpty();
		}
	}

	@Nested
	@SpringBootTest(properties = "board.member-backfill.enabled=true")
	@ActiveProfiles("test")
	@DisplayName("켜면")
	class EnabledByProperty {
		@MockitoBean
		private MemberReplicaPort port;

		@Test
		@DisplayName("애플리케이션이 다 뜬 뒤 회원 서비스에서 첫 쪽을 받아 온다")
		void startsAfterReady() {
			verify(port, timeout(5000)).fetch(0, 500);
		}
	}

	@Nested
	@SpringBootTest(properties = "board.member-backfill.enabled=true")
	@ActiveProfiles("test")
	@Import(MemberServiceDown.DownPort.class)
	@DisplayName("회원 서비스가 꺼져 있어도")
	class MemberServiceDown {
		static final AtomicInteger CALLS = new AtomicInteger();

		@Autowired
		private ConfigurableApplicationContext context;

		@Test
		@DisplayName("게시판은 정상으로 떠 있다 (채우기만 오류를 로그로 남기고 끝남)")
		void boardStaysUp() throws Exception {
			long deadline = System.currentTimeMillis() + 5000;
			while (CALLS.get() == 0 && System.currentTimeMillis() < deadline) {
				Thread.sleep(50);
			}

			assertThat(CALLS.get()).isEqualTo(1);
			assertThat(context.isActive()).isTrue();
		}

		@TestConfiguration
		static class DownPort {
			@Bean
			MemberReplicaPort memberReplicaPort() {
				return (afterId, size) -> {
					CALLS.incrementAndGet();
					throw new RestClientException("회원 서비스 꺼짐");
				};
			}
		}
	}
}
