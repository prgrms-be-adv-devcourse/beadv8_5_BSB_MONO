package com.bukang.cash.app.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.cash.app.CashFacade;
import com.bukang.cash.domain.member.CashMember;
import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.wallet.WalletRepository;

@SpringBootTest
@ActiveProfiles("test")
class CashSyncMemberUseCaseTest {
	private static final long MEMBER_ID = 5;

	@Autowired
	private CashFacade cashFacade;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private WalletRepository walletRepository;

	@AfterEach
	void tearDown() {
		walletRepository.deleteAll();
		cashMemberRepository.deleteAll();
	}

	@Test
	@DisplayName("새 회원을 동기화하면 CashMember와 Wallet이 한 번의 호출로 함께 생성된다")
	void syncMember_newMember_createsWalletTogether() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);

		CashMember cashMember = cashFacade.syncMember(MEMBER_ID, now, now, "러너5");

		assertThat(cashMember.getId()).isEqualTo(MEMBER_ID);
		assertThat(cashMemberRepository.findById(MEMBER_ID)).isPresent();
		assertThat(walletRepository.findById(MEMBER_ID))
			.as("새 회원이면 같은 호출 안에서 지갑도 바로 만들어져야 한다")
			.isPresent();
	}

	@Test
	@DisplayName("이미 있는 회원을 다시 동기화해도(정보 수정) 지갑을 새로 만들지 않는다")
	void syncMember_existingMember_doesNotCreateAnotherWallet() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 0);
		cashFacade.syncMember(MEMBER_ID, now, now, "러너5");
		int walletCountAfterFirstSync = (int) walletRepository.count();

		cashFacade.syncMember(MEMBER_ID, now, now.plusDays(1), "러너5-수정");

		assertThat(walletRepository.count()).isEqualTo(walletCountAfterFirstSync);
		assertThat(cashMemberRepository.findById(MEMBER_ID).orElseThrow().getNickname())
			.isEqualTo("러너5-수정");
	}

	@Test
	@DisplayName("같은 가입 메시지가 두 번 와도 회원과 지갑은 하나씩이다 (멱등)")
	void syncMember_sameMessageTwice_keepsOneMemberAndWallet() {
		LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 16, 10, 0);

		cashFacade.syncMember(MEMBER_ID, joinedAt, joinedAt, "러너5");
		cashFacade.syncMember(MEMBER_ID, joinedAt, joinedAt, "러너5");

		assertThat(cashMemberRepository.count()).isEqualTo(1);
		assertThat(walletRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("닉네임 수정(10:05)을 반영한 뒤 재시도로 늦게 온 가입 메시지(10:00)는 건너뛰어 수정한 닉네임이 남는다")
	void syncMember_olderJoinedAfterModified_keepsNewerNickname() {
		LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 16, 10, 0);
		LocalDateTime modifiedAt = joinedAt.plusMinutes(5);
		cashFacade.syncMember(MEMBER_ID, joinedAt, modifiedAt, "영희");

		cashFacade.syncMember(MEMBER_ID, joinedAt, joinedAt, "철수");

		CashMember member = cashMemberRepository.findById(MEMBER_ID).orElseThrow();
		assertThat(member.getNickname()).isEqualTo("영희");
		assertThat(member.getModifyDate()).isEqualTo(modifiedAt);
	}

	@Test
	@DisplayName("탈퇴 메시지(수정 일시 = 탈퇴 시각)가 오면 닉네임이 \"탈퇴한 회원\"으로 바뀐다")
	void syncMember_withdrawnMessage_overwritesNickname() {
		LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 16, 10, 0);
		cashFacade.syncMember(MEMBER_ID, joinedAt, joinedAt, "러너5");

		cashFacade.syncMember(MEMBER_ID, joinedAt, joinedAt.plusDays(3), "탈퇴한 회원");

		assertThat(cashMemberRepository.findById(MEMBER_ID).orElseThrow().getNickname()).isEqualTo("탈퇴한 회원");
	}

	@Test
	@DisplayName("가입(10:00)과 수정(10:05) 메시지를 동시에 처음 처리해도, 실패한 쪽을 다시 처리하면 수정한 닉네임 하나로 맞춰진다")
	void syncMember_joinedAndModifiedAtSameTime_convergesAfterRetry() throws InterruptedException {
		LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 16, 10, 0);
		LocalDateTime modifiedAt = joinedAt.plusMinutes(5);
		// 스레드가 겹치는 순서는 매번 달라서 회원을 바꿔 가며 여러 번 돌린다
		for (long memberId = 100; memberId < 120; memberId++) {
			long id = memberId;
			List<Runnable> messages = List.of(
				() -> cashFacade.syncMember(id, joinedAt, joinedAt, "철수"),
				() -> cashFacade.syncMember(id, joinedAt, modifiedAt, "영희"));

			// 둘 다 "아직 없음"을 읽으면 둘 다 INSERT해 한쪽이 기본키 충돌로 실패한다
			// 실제로는 실패한 메시지가 재시도 토픽에서 다시 처리되므로, 여기서도 실패한 쪽을 한 번 더 부른다
			runConcurrently(messages).forEach(Runnable::run);

			CashMember member = cashMemberRepository.findById(id).orElseThrow();
			assertThat(member.getNickname()).as("memberId=%d", id).isEqualTo("영희");
			assertThat(member.getModifyDate()).as("memberId=%d", id).isEqualTo(modifiedAt);
		}
		assertThat(cashMemberRepository.count()).isEqualTo(20);
		assertThat(walletRepository.count()).as("회원마다 지갑은 하나다").isEqualTo(20);
	}

	@Test
	@DisplayName("이미 있는 회원에게 수정(10:05)과 수정(10:06)이 동시에 처리돼도, 수정 시각이 더 늦은 닉네임이 남는다")
	void syncMember_twoModifiedAtSameTime_keepsLatestNickname() throws InterruptedException {
		LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 16, 10, 0);
		LocalDateTime olderModifiedAt = joinedAt.plusMinutes(5);
		LocalDateTime newerModifiedAt = joinedAt.plusMinutes(6);
		// 재시도 토픽에서 다시 처리되는 옛 수정 메시지와 메인 토픽의 새 수정 메시지가 겹치는 경우다
		for (long memberId = 200; memberId < 220; memberId++) {
			long id = memberId;
			cashFacade.syncMember(id, joinedAt, joinedAt, "러너");
			List<Runnable> messages = List.of(
				() -> cashFacade.syncMember(id, joinedAt, olderModifiedAt, "옛 닉네임"),
				() -> cashFacade.syncMember(id, joinedAt, newerModifiedAt, "새 닉네임"));

			runConcurrently(messages).forEach(Runnable::run);

			CashMember member = cashMemberRepository.findById(id).orElseThrow();
			assertThat(member.getNickname()).as("memberId=%d", id).isEqualTo("새 닉네임");
			assertThat(member.getModifyDate()).as("memberId=%d", id).isEqualTo(newerModifiedAt);
		}
	}

	// 메시지를 동시에 처리하고, 예외가 난 메시지를 돌려준다
	private List<Runnable> runConcurrently(List<Runnable> messages) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(messages.size());
		CountDownLatch startSignal = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (Runnable message : messages) {
			futures.add(executor.submit(() -> {
				startSignal.await();
				message.run();
				return null;
			}));
		}
		startSignal.countDown();

		List<Runnable> failed = new ArrayList<>();
		for (int i = 0; i < messages.size(); i++) {
			try {
				futures.get(i).get();
			} catch (ExecutionException exception) {
				failed.add(messages.get(i));
			}
		}
		executor.shutdown();
		return failed;
	}
}
