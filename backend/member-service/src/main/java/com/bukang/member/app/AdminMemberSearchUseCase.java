package com.bukang.member.app;

import java.util.List;
import java.util.function.LongFunction;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.exception.AdminMemberSearchNicknameRequiredException;
import com.bukang.member.domain.exception.WithdrawCheckFailedException;
import com.bukang.member.dto.AdminMemberSearchDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.order.ActiveTicketPort;

import lombok.RequiredArgsConstructor;

// 관리자 회원 관리 「전체 회원」 닉네임 찾기 (CRU-156)
// 닉네임 앞부분이 같은 회원을 최대 20명 찾고, 예치금·참가권 수는 각 서비스 내부 API에 회원마다 묻는다
// 다른 서비스를 부르는 동안 DB 연결을 잡지 않도록 트랜잭션을 걸지 않는다
@Service
@RequiredArgsConstructor
public class AdminMemberSearchUseCase {
	private final AdminVerifyUseCase adminVerifyUseCase;
	private final MemberRepository memberRepository;
	private final CashBalancePort cashBalancePort;
	private final ActiveTicketPort activeTicketPort;

	public List<AdminMemberSearchDto> search(long adminId, String nickname) {
		adminVerifyUseCase.verify(adminId);
		String prefix = nickname == null ? "" : nickname.strip();
		if (prefix.isEmpty()) {
			throw new AdminMemberSearchNicknameRequiredException();
		}
		Lookup<Long> deposits = new Lookup<>(cashBalancePort::findBalance);
		Lookup<Integer> tickets = new Lookup<>(activeTicketPort::countActiveTickets);
		return memberRepository.findTop20ByNicknameStartingWithAndStatusNotOrderByNicknameAsc(prefix,
				MemberStatus.WITHDRAWN).stream()
			.map(member -> new AdminMemberSearchDto(member.getId(), member.getNickname(), member.getCreateDate(),
				deposits.get(member.getId()), tickets.get(member.getId())))
			.toList();
	}

	// 묻지 못한 값은 null로 두고 찾기는 계속한다. 한 번 실패한 서비스는 남은 회원에게 다시 묻지 않아,
	// 멈춘 서비스의 시간 제한을 회원 수만큼 기다리지 않는다 (실패 로그는 어댑터가 남긴다)
	private static final class Lookup<T> {
		private final LongFunction<T> query;
		private boolean failed;

		private Lookup(LongFunction<T> query) {
			this.query = query;
		}

		private T get(long memberId) {
			if (failed) {
				return null;
			}
			try {
				return query.apply(memberId);
			} catch (WithdrawCheckFailedException exception) {
				failed = true;
				return null;
			}
		}
	}
}
