package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaEventHeaders;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MarketingConsentChangedEvent;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.event.MemberWithdrawnEvent;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.MemberOutboxStatus;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.TermsType;
import com.bukang.member.domain.exception.DuplicateEmailException;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberModifyRequestDto;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.dto.SellerDto;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.identity.VerifiedIdentity;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.out.payoutaccount.InMemoryPayoutAccountVerifyLimiter;

/**
 * 회원·판매자 이벤트가 회원 데이터와 같은 트랜잭션에서 아웃박스 행으로 남는지 확인한다 (BUK-41)
 * - 롤백되면 행도 없고, 트랜잭션 밖에서 저장하면 예외가 난다 (MANDATORY)
 * - 본문의 eventId와 행의 event_id가 같고, 대상 ID가 행의 ref_id와 같다
 * Kafka로 보내는 일은 MemberOutboxRelayTest, 브로커를 거친 헤더는 MemberOutboxKafkaTest가 확인한다
 * 예치금·주문 서비스와 Redis는 띄우지 않으므로 탈퇴 확인 포트와 Refresh Token 저장소를 목으로 바꾼다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, InMemoryPayoutAccountVerifyLimiter.Config.class})
class MemberOutboxAppendTest {
	private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberJoinUseCase memberJoinUseCase;
	@Autowired
	private SellerFacade sellerFacade;
	@Autowired
	private MemberOutboxAppendUseCase memberOutboxAppendUseCase;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private SellerRepository sellerRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private ApplicationEventPublisher eventPublisher;
	@Autowired
	private JsonConverter jsonConverter;

	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;
	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void setUp() {
		// 같은 H2를 쓰는 다른 테스트가 남긴 행이 섞이지 않게 비우고 시작한다
		memberOutboxEventRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		// 판매자·동의 기록이 회원을 참조하므로(FK) 먼저 지운다
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입하면 같은 트랜잭션에서 가입 토픽 행이 보낼 차례로 남고, 본문 eventId·회원 ID·마케팅 동의가 행과 맞는다")
	void joinAppendsJoinedRow() {
		MemberDto member = join("outbox-join@crewrun.com", "아웃박스가입", "010-3000-0001",
			List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING));

		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows).hasSize(1);
		MemberOutboxEvent row = rows.get(0);
		MemberJoinedEvent body = jsonConverter.fromJson(row.getPayload(), MemberJoinedEvent.class);
		assertThat(row.getTopic()).isEqualTo(KafkaTopics.MEMBER_MEMBER_JOINED);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRetryCount()).isZero();
		assertThat(row.getRefType()).isEqualTo("Member");
		assertThat(row.getRefId()).isEqualTo(member.getId());
		assertThat(row.getOccurredAt()).isNotNull();
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(UUID.fromString(row.getEventId()).toString()).isEqualTo(row.getEventId());
		assertThat(body.id()).isEqualTo(member.getId());
		assertThat(body.nickname()).isEqualTo("아웃박스가입");
		assertThat(body.marketingAgreed()).isTrue();
	}

	@Test
	@DisplayName("마케팅 약관에 동의하지 않고 가입하면 가입 본문의 marketingAgreed가 false다")
	void joinWithoutMarketingConsent() {
		join("outbox-nomarketing@crewrun.com", "마케팅거절", "010-3000-0002",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));

		MemberJoinedEvent body = jsonConverter.fromJson(allRows().get(0).getPayload(), MemberJoinedEvent.class);
		assertThat(body.marketingAgreed()).isFalse();
	}

	@Test
	@DisplayName("가입 트랜잭션이 롤백되면 아웃박스 행도 남지 않는다")
	void joinRollbackLeavesNoRow() {
		transactionTemplate.executeWithoutResult(status -> {
			memberJoinUseCase.join("outbox-rollback@crewrun.com", "password1234", "롤백가입", null,
				List.of(TermsType.SERVICE, TermsType.PRIVACY),
				new VerifiedIdentity("김롤백", BIRTH_DATE, "010-3000-0003", "ci-outbox-rollback", null));
			status.setRollbackOnly();
		});

		assertThat(memberOutboxEventRepository.count()).isZero();
		assertThat(memberRepository.existsByEmail("outbox-rollback@crewrun.com")).isFalse();
	}

	@Test
	@DisplayName("중복 이메일로 가입에 실패하면 실패한 가입의 아웃박스 행은 남지 않는다")
	void duplicateJoinLeavesNoRow() {
		join("outbox-dup@crewrun.com", "먼저가입", "010-3000-0004", List.of(TermsType.SERVICE, TermsType.PRIVACY));

		assertThatThrownBy(() -> join("outbox-dup@crewrun.com", "중복가입", "010-3000-0005",
			List.of(TermsType.SERVICE, TermsType.PRIVACY)))
			.isInstanceOf(DuplicateEmailException.class);

		assertThat(allRows()).hasSize(1);
	}

	@Test
	@DisplayName("정보를 바꾸면 정보 변경 토픽 행이 남고, 본문의 eventId·닉네임·수정 시각이 행·회원과 맞는다")
	void modifyAppendsModifiedRow() {
		MemberDto joined = join("outbox-modify@crewrun.com", "수정전회원", "010-3000-0006",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));

		MemberDto modified = memberFacade.modifyMe(joined.getId(),
			new MemberModifyRequestDto("김러너", "수정후회원", "010-3000-0006", BIRTH_DATE));

		MemberOutboxEvent row = allRows().get(1);
		MemberModifiedEvent body = jsonConverter.fromJson(row.getPayload(), MemberModifiedEvent.class);
		assertThat(row.getTopic()).isEqualTo(KafkaTopics.MEMBER_MODIFIED);
		assertThat(row.getRefId()).isEqualTo(joined.getId());
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(body.nickname()).isEqualTo("수정후회원");
		assertThat(body.modifyDate()).isEqualTo(modified.getModifyDate());
	}

	@Test
	@DisplayName("탈퇴하면 한 트랜잭션에 정보 변경(닉네임 \"탈퇴한 회원\") → 탈퇴 순서로 두 줄이 남고, 이벤트 ID는 서로 다르다")
	void withdrawAppendsModifiedThenWithdrawn() {
		MemberDto joined = join("outbox-withdraw@crewrun.com", "탈퇴할회원", "010-3000-0007",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));

		memberFacade.withdrawMe(joined.getId());

		List<MemberOutboxEvent> rows = allRows();
		assertThat(rows).extracting(MemberOutboxEvent::getTopic)
			.containsExactly(
				KafkaTopics.MEMBER_MEMBER_JOINED, KafkaTopics.MEMBER_MODIFIED, KafkaTopics.MEMBER_WITHDRAWN);
		MemberModifiedEvent modifiedBody = jsonConverter.fromJson(rows.get(1).getPayload(), MemberModifiedEvent.class);
		MemberWithdrawnEvent withdrawnBody =
			jsonConverter.fromJson(rows.get(2).getPayload(), MemberWithdrawnEvent.class);
		assertThat(modifiedBody.nickname()).isEqualTo(Member.WITHDRAWN_NICKNAME);
		assertThat(withdrawnBody.eventId()).isEqualTo(rows.get(2).getEventId());
		assertThat(withdrawnBody.id()).isEqualTo(joined.getId());
		assertThat(withdrawnBody.withdrawnAt()).isNotNull();
		assertThat(rows.get(2).getRefId()).isEqualTo(joined.getId());
		assertThat(rows.get(1).getEventId()).isNotEqualTo(rows.get(2).getEventId());
		// 탈퇴 본문에는 회원 ID와 시각만 있다 (이메일·이름·휴대폰은 넣지 않는다)
		assertThat(rows.get(2).getPayload()).doesNotContain("outbox-withdraw@crewrun.com", "010-3000-0007");
	}

	@Test
	@DisplayName("판매자 신청·승인은 판매자 토픽 행을 남기고, 대상 종류는 Seller, 대상 ID는 판매자 ID다")
	void sellerApplyAndApproveAppendSellerRows() {
		MemberDto member = join("outbox-seller@crewrun.com", "판매신청", "010-3000-0008",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));
		MemberDto admin = join("outbox-admin@crewrun.com", "관리회원", "010-3000-0009",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));
		memberFacade.grantAdmin("outbox-admin@crewrun.com");

		SellerDto seller = sellerFacade.apply(member.getId(),
			new SellerApplyRequestDto(SellerType.BUSINESS, "크루런 스포츠", "1234567890", "신청대표",
				"004", "1101230000678", PayoutAccountHolderType.PERSONAL));
		sellerFacade.approve(admin.getId(), seller.getId());

		List<MemberOutboxEvent> sellerRows = allRows().stream()
			.filter(row -> KafkaTopics.SELLER_CHANGED.equals(row.getTopic()))
			.toList();
		assertThat(sellerRows).hasSize(2);
		assertThat(sellerRows).allSatisfy(row -> {
			SellerChangedEvent body = jsonConverter.fromJson(row.getPayload(), SellerChangedEvent.class);
			assertThat(row.getRefType()).isEqualTo("Seller");
			assertThat(row.getRefId()).isEqualTo(seller.getId());
			assertThat(body.eventId()).isEqualTo(row.getEventId());
		});
	}

	@Test
	@DisplayName("마케팅 수신 동의 변경을 발행하면 동의 변경 토픽 행이 남고, 본문에 회원 ID·동의 여부·바꾼 시각이 있다")
	void marketingConsentChangeAppendsRow() {
		MemberDto joined = join("outbox-consent@crewrun.com", "동의변경", "010-3000-0010",
			List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING));
		LocalDateTime changedAt = LocalDateTime.of(2026, 11, 2, 15, 0);

		transactionTemplate.executeWithoutResult(status -> memberRepository.findById(joined.getId()).orElseThrow()
			.publishMarketingConsentChangedEvent(false, changedAt));

		MemberOutboxEvent row = allRows().get(1);
		MarketingConsentChangedEvent body =
			jsonConverter.fromJson(row.getPayload(), MarketingConsentChangedEvent.class);
		assertThat(row.getTopic()).isEqualTo(KafkaTopics.MARKETING_CONSENT_CHANGED);
		assertThat(row.getRefType()).isEqualTo("Member");
		assertThat(row.getRefId()).isEqualTo(joined.getId());
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(body.memberId()).isEqualTo(joined.getId());
		assertThat(body.agreed()).isFalse();
		assertThat(body.changedAt()).isEqualTo(changedAt);
	}

	@Test
	@DisplayName("트랜잭션 밖에서 아웃박스에 저장하면 조용히 버리지 않고 예외가 나며 행이 남지 않는다 (MANDATORY)")
	void appendOutsideTransactionFails() {
		MemberJoinedEvent event = new MemberJoinedEvent(1L, LocalDateTime.now(), LocalDateTime.now(), "밖에서",
			false, UUID.randomUUID().toString());

		assertThatThrownBy(() -> memberOutboxAppendUseCase.appendMemberJoined(event))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("이벤트 ID가 빈 이벤트(받는 쪽용 옛 생성자)를 발행하면 예외가 나고 그 트랜잭션이 롤백되어 행이 남지 않는다")
	void blankEventIdRollsBack() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
			eventPublisher.publishEvent(new MemberJoinedEvent(1L, LocalDateTime.now(), LocalDateTime.now(), "옛생성자"))))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("새 필드(eventId, marketingAgreed)를 모르는 옛 소비자 레코드도 새 가입 본문을 읽는다")
	void oldConsumerReadsNewPayload() {
		MemberDto member = join("outbox-oldconsumer@crewrun.com", "옛소비자", "010-3000-0011",
			List.of(TermsType.SERVICE, TermsType.PRIVACY));

		OldMemberJoinedEvent old = jsonConverter.fromJson(allRows().get(0).getPayload(), OldMemberJoinedEvent.class);

		assertThat(old.id()).isEqualTo(member.getId());
		assertThat(old.nickname()).isEqualTo("옛소비자");
	}

	@Test
	@DisplayName("새 필드가 없는 옛 본문(배포 중 옛 서버가 보낸 메시지)도 새 이벤트 레코드로 읽는다")
	void newConsumerReadsOldPayload() {
		String oldJoined = "{\"id\":1001,\"createDate\":\"2026-10-14T10:00:00\","
			+ "\"modifyDate\":\"2026-10-14T10:00:00\",\"nickname\":\"테스터\"}";
		String oldModified = oldJoined;
		String oldSeller = "{\"id\":7,\"memberId\":1001,\"sellerType\":\"INDIVIDUAL\",\"representativeName\":\"대표\","
			+ "\"status\":\"PENDING\",\"requestedAt\":\"2026-10-14T10:00:00\"}";

		MemberJoinedEvent joined = jsonConverter.fromJson(oldJoined, MemberJoinedEvent.class);
		MemberModifiedEvent modified = jsonConverter.fromJson(oldModified, MemberModifiedEvent.class);
		SellerChangedEvent seller = jsonConverter.fromJson(oldSeller, SellerChangedEvent.class);

		assertThat(joined.id()).isEqualTo(1001L);
		assertThat(joined.marketingAgreed()).isNotEqualTo(Boolean.TRUE);
		assertThat(joined.eventId()).isNull();
		assertThat(modified.eventId()).isNull();
		assertThat(seller.eventId()).isNull();
	}

	@Test
	@DisplayName("토픽·헤더 이름이 받는 쪽과 약속한 문자열 그대로다")
	void contractNames() {
		assertThat(KafkaTopics.MEMBER_MEMBER_JOINED).isEqualTo("queuing.member.joined");
		assertThat(KafkaTopics.MEMBER_MODIFIED).isEqualTo("queuing.member.modified");
		assertThat(KafkaTopics.MEMBER_WITHDRAWN).isEqualTo("queuing.member.withdrawn");
		assertThat(KafkaTopics.MARKETING_CONSENT_CHANGED).isEqualTo("queuing.member.consentchanged");
		assertThat(KafkaTopics.SELLER_CHANGED).isEqualTo("queuing.seller.changed");
		assertThat(KafkaEventHeaders.EVENT_ID).isEqualTo("eventId");
		assertThat(KafkaEventHeaders.EVENT_TYPE).isEqualTo("eventType");
		assertThat(KafkaEventHeaders.OCCURRED_AT).isEqualTo("occurredAt");
		assertThat(KafkaEventHeaders.AGGREGATE_TYPE).isEqualTo("aggregateType");
		assertThat(KafkaEventHeaders.AGGREGATE_ID).isEqualTo("aggregateId");
	}

	// 이번 변경 전 가입 이벤트 모양 (예치금·상품 서비스의 옛 버전이 읽는 필드)
	private record OldMemberJoinedEvent(long id, LocalDateTime createDate, LocalDateTime modifyDate, String nickname) {
	}

	private MemberDto join(String email, String nickname, String phone, List<TermsType> agreedTerms) {
		return memberFacade.join(identityStore.joinRequest(
			email, "password1234", "김러너", nickname, phone, BIRTH_DATE, null, agreedTerms));
	}

	private List<MemberOutboxEvent> allRows() {
		return memberOutboxEventRepository.findAll().stream()
			.sorted(Comparator.comparing(MemberOutboxEvent::getId))
			.toList();
	}
}
