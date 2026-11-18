package com.bukang.member.app;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.dto.MarketingConsentDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.TermsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 마케팅 수신 동의 조회·변경 (BUK-70). 동의 기록은 고치지 않고 새 줄로 쌓는다 (BE-021)
 * 바꾸면 같은 트랜잭션에서 동의 변경 이벤트가 아웃박스에 남는다. 아웃박스 저장이 실패하면 기록도 롤백된다
 * 같은 값이면 기록·이벤트를 남기지 않는다. 동의 시각이 처음 동의한 날로 남아야 2년마다 다시 묻는 기준이 된다
 */
@Service
@RequiredArgsConstructor
public class MarketingConsentUseCase {
	private final Clock clock;
	private final MemberRepository memberRepository;
	private final TermsRepository termsRepository;
	private final TermsAgreementRepository termsAgreementRepository;

	public MarketingConsentDto find(long memberId) {
		return find(memberId, TermsType.MARKETING);
	}

	public MarketingConsentDto change(long memberId, boolean agreed) {
		return change(memberId, TermsType.MARKETING, agreed);
	}

	// 야간 광고 수신 동의도 같은 방식으로 다룬다 (CRU-22). type은 MARKETING 또는 NIGHT_MARKETING
	public MarketingConsentDto find(long memberId, TermsType type) {
		memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		return toDto(latest(memberId, type));
	}

	// 동의 변경 이벤트는 마케팅 동의만 보낸다. 야간 광고 동의는 받는 서비스가 아직 없어 기록만 쌓는다
	public MarketingConsentDto change(long memberId, TermsType type, boolean agreed) {
		// 회원 행을 잠가 같은 회원의 변경을 한 줄로 세운다. 그래야 마지막 기록과 마지막 이벤트의 값이 같다
		// 이 잠금이 트랜잭션의 첫 쿼리여야 한다. MySQL 기본 격리 수준은 첫 일반 조회 때 읽는 시점이 고정되어,
		// 앞에 다른 조회가 있으면 뒤 요청이 앞 요청이 쌓은 줄을 못 본다 (H2 테스트로는 잡히지 않는다)
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

		Optional<TermsAgreement> current = latest(memberId, type);
		// 기록이 없으면(마케팅 약관이 생기기 전 가입) 미동의로 본다
		if (current.map(TermsAgreement::isAgreed).orElse(false) == agreed) {
			return toDto(current);
		}

		// 지금 버전의 약관에 동의·거부한 것으로 남긴다. TermsDataInit이 켜질 때 만들어 두므로 없으면 설정 오류다
		Terms marketing = termsRepository.findCurrent(ClockTimes.today(clock)).stream()
			.filter(terms -> terms.getType() == type)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("시행 중인 " + type + " 약관이 없습니다."));
		// DB가 마이크로초까지 저장하므로 맞춰 두어, 기록의 결정 시각과 이벤트의 바꾼 시각이 같게 한다
		LocalDateTime decidedAt = ClockTimes.now(clock).truncatedTo(ChronoUnit.MICROS);
		TermsAgreement saved = termsAgreementRepository.save(new TermsAgreement(member, marketing, agreed, decidedAt));
		if (type == TermsType.MARKETING) {
			member.publishMarketingConsentChangedEvent(agreed, decidedAt);
		}
		return toDto(Optional.of(saved));
	}

	private Optional<TermsAgreement> latest(long memberId, TermsType type) {
		return termsAgreementRepository.findFirstByMemberIdAndTermsTypeOrderByIdDesc(memberId, type);
	}

	private static MarketingConsentDto toDto(Optional<TermsAgreement> agreement) {
		return agreement
			.map(found -> new MarketingConsentDto(found.isAgreed(), found.getDecidedAt()))
			.orElse(new MarketingConsentDto(false, null));
	}
}
