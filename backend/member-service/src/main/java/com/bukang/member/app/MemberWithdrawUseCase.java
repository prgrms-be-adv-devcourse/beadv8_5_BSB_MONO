package com.bukang.member.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.crew.app.CrewMembershipService;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.WithdrawCashLeftException;
import com.bukang.member.domain.exception.WithdrawCrewLeaderException;
import com.bukang.member.domain.exception.WithdrawTicketLeftException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.order.ActiveTicketPort;

import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴 (BE-022). 남은 예치금·참가권이 없을 때만 바로 WITHDRAWN으로 끝내고 개인정보 칸을 비운다
 * 확인 순서: 이미 탈퇴 → 404, 크루장인 크루 있음 → 409, 예치금 > 0 → 409, 끝나지 않은 참가권 ≥ 1 → 409,
 * 확인 실패 → 503 (아무것도 바꾸지 않음)
 * 크루장이 아닌 회원은 탈퇴와 같은 트랜잭션에서 모든 크루의 크루원 행을 지운다 (BE-029)
 * 호출하는 쪽(MemberFacade)의 트랜잭션 하나에서 처리하고, 다른 서비스 사본은 커밋 뒤 수정 이벤트로 덮는다
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawUseCase {
	static final String NOT_FOUND_MESSAGE = "존재하지 않는 회원입니다.";
	static final String CREW_LEADER_MESSAGE = "크루장인 크루가 있어 탈퇴할 수 없습니다. 크루장을 넘기거나 크루를 없앤 뒤 탈퇴해주세요.";
	static final String CASH_LEFT_MESSAGE = "남은 예치금이 있어 탈퇴할 수 없습니다. 예치금을 모두 사용하거나 출금한 뒤 탈퇴해주세요.";
	static final String TICKET_LEFT_MESSAGE = "끝나지 않은 참가권이 있어 탈퇴할 수 없습니다. 대회가 끝나거나 참가권을 취소한 뒤 탈퇴해주세요.";

	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final CashBalancePort cashBalancePort;
	private final ActiveTicketPort activeTicketPort;
	private final CrewMembershipService crewMembershipService;

	public void withdraw(long memberId) {
		// 줄을 잠가 같은 회원의 탈퇴가 겹치지 않게 한다. 뒤 요청은 앞 요청이 커밋한 뒤 탈퇴한 회원을 보고 404가 된다
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException(NOT_FOUND_MESSAGE));

		// 크루장인 크루가 있으면 크루장을 넘기거나 크루를 없앤 뒤에만 탈퇴할 수 있다
		if (crewMembershipService.isLeaderOfAnyCrew(memberId)) {
			throw new WithdrawCrewLeaderException(CREW_LEADER_MESSAGE);
		}

		// 다른 서비스 확인이 실패하면 예외가 그대로 나가 아무것도 바꾸지 않는다 (503)
		if (cashBalancePort.findBalance(memberId) > 0) {
			throw new WithdrawCashLeftException(CASH_LEFT_MESSAGE);
		}
		if (activeTicketPort.countActiveTickets(memberId) > 0) {
			throw new WithdrawTicketLeftException(TICKET_LEFT_MESSAGE);
		}

		LocalDateTime now = LocalDateTime.now();
		member.withdraw(now);
		// CHECK 제약 위반 같은 DB 오류를 이 트랜잭션 안에서 드러내려고 바로 반영한다
		memberRepository.saveAndFlush(member);
		// 탈퇴 전에 낸 대기 중 판매자 신청은 반려한다. 남겨 두면 관리자가 승인해 탈퇴한 회원이 판매자가 된다 (PRO-217)
		sellerRepository.findAllByMemberIdAndStatusForUpdate(memberId, SellerStatus.PENDING)
			.forEach(seller -> seller.rejectByWithdrawal(now));
		// 탈퇴한 회원이 크루원 수에 섞이지 않게 모든 크루에서 뺀다
		crewMembershipService.leaveAllCrews(memberId);
		// 그 회원의 모든 기기 재발급을 막는다. Redis 오류면 예외가 나가 탈퇴도 롤백된다
		refreshTokenRepository.deleteAll(memberId);
		// 커밋된 뒤에만 Kafka로 나간다 (MemberKafkaPublisher, AFTER_COMMIT)
		member.publishWithdrawnEvent();
	}
}
