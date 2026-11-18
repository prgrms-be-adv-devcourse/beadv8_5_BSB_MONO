package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.TermsRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

/**
 * 마케팅에 동의한 지 2년 된 회원에게 재동의 알림을 요청한다 (CRU-22)
 * - 마지막 마케팅 기록이 "동의"이고 동의 시각이 2년 전인 회원만 대상이다
 * - 2년마다(2·4·6년…) 다시 알린다. 동의 기록은 같은 값이면 새로 쌓이지 않아 처음 동의 시각이 기준이다
 * - 시각은 유스케이스에 직접 넣는다. 알림 ID는 동의 기록·햇수마다 고정이라 며칠 겹쳐 보내도 받는 쪽이 하나만 저장한다
 */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(InMemoryIdentityVerificationStore.Config.class)
class MarketingReconsentUseCaseTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
	private static final LocalDateTime TWO_YEARS_AGO = NOW.minusYears(2);

	@Autowired
	private MarketingReconsentUseCase marketingReconsentUseCase;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsRepository termsRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private ApplicationEvents applicationEvents;

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("동의한 지 2년 된 회원에게만 재동의 알림을 요청하고, 알림 ID는 동의 기록으로 고정된다")
	void notifiesOnlyDueMembers() {
		Member due = member();
		TermsAgreement dueAgreement = agree(due, true, TWO_YEARS_AGO.minusHours(1));
		Member notYet = member();
		agree(notYet, true, NOW.minusYears(1));
		Member never = member();

		int notified = marketingReconsentUseCase.notifyDue(NOW);

		assertThat(notified).isOne();
		List<NotificationRequestedEvent> events = applicationEvents.stream(NotificationRequestedEvent.class).toList();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.memberId()).isEqualTo(due.getId());
			assertThat(event.kind()).isEqualTo(NotificationKind.MARKETING_RECONSENT);
			assertThat(event.eventId()).isEqualTo("marketing-reconsent-" + dueAgreement.getId() + "-2");
		});
		assertThat(never.getId()).isNotEqualTo(due.getId());
	}

	@Test
	@DisplayName("2년마다 다시 알린다: 4년 된 동의는 대상이고, 3년 된 동의는 대상이 아니다")
	void notifiesEveryTwoYears() {
		Member fourYears = member();
		TermsAgreement fourYearsAgreement = agree(fourYears, true, NOW.minusYears(4).minusHours(1));
		Member threeYears = member();
		agree(threeYears, true, NOW.minusYears(3));

		assertThat(marketingReconsentUseCase.notifyDue(NOW)).isOne();
		assertThat(applicationEvents.stream(NotificationRequestedEvent.class).toList())
			.singleElement().satisfies(event -> {
				assertThat(event.memberId()).isEqualTo(fourYears.getId());
				assertThat(event.eventId()).isEqualTo("marketing-reconsent-" + fourYearsAgreement.getId() + "-4");
			});
	}

	@Test
	@DisplayName("마지막 기록이 거부이거나 탈퇴한 회원은 대상이 아니다")
	void skipsWithdrawnConsentAndWithdrawnMember() {
		Member turnedOff = member();
		agree(turnedOff, true, TWO_YEARS_AGO.minusHours(1));
		agree(turnedOff, false, NOW.minusMonths(1));
		Member withdrawn = member();
		agree(withdrawn, true, TWO_YEARS_AGO.minusHours(1));
		withdrawn.withdraw(NOW.minusDays(1));
		memberRepository.save(withdrawn);

		assertThat(marketingReconsentUseCase.notifyDue(NOW)).isZero();
	}

	@Test
	@DisplayName("하루 놓쳐도 3일 안에는 다시 대상이 되고, 3일이 지나면 대상이 아니다")
	void windowIsThreeDays() {
		Member lateByTwoDays = member();
		agree(lateByTwoDays, true, TWO_YEARS_AGO.minusDays(2));
		Member lateByFourDays = member();
		agree(lateByFourDays, true, TWO_YEARS_AGO.minusDays(4));

		assertThat(marketingReconsentUseCase.notifyDue(NOW)).isOne();
		assertThat(applicationEvents.stream(NotificationRequestedEvent.class).toList())
			.extracting(NotificationRequestedEvent::memberId)
			.containsExactly(lateByTwoDays.getId());
	}

	private Member member() {
		long id = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		return memberRepository.findById(id).orElseThrow();
	}

	private TermsAgreement agree(Member member, boolean agreed, LocalDateTime decidedAt) {
		Terms marketing = termsRepository.findCurrent(LocalDate.now()).stream()
			.filter(terms -> terms.getType() == TermsType.MARKETING)
			.findFirst().orElseThrow();
		return termsAgreementRepository.save(new TermsAgreement(member, marketing, agreed, decidedAt));
	}
}
