package com.bukang.member.dto;

import java.time.LocalDateTime;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 다른 서비스가 회원 복사본을 처음 채울 때 받는 회원 한 명 (내부 API, BUK-161)
 * 공개 정보 여섯 칸만 담는다. 이름, 이메일, 휴대폰 번호, 생년월일 같은 개인정보는 넣지 않는다
 */
@Schema(description = "회원 복사본 한 명 (서비스 간 내부 호출용)")
public record MemberReplicaDto(
	@Schema(description = "회원 ID", example = "1")
	long memberId,
	@Schema(description = "닉네임. 탈퇴한 회원이면 「탈퇴한 회원」", example = "러너A")
	String nickname,
	@Schema(description = "가장 최근 판매자 신청이 승인 상태면 true. 신청이 없거나 대기·반려면 false", example = "false")
	boolean sellerApproved,
	@Schema(description = "탈퇴한 회원이면 true", example = "false")
	boolean withdrawn,
	@Schema(description = "회원 가입 시각", example = "2026-10-01T10:00:00")
	LocalDateTime createDate,
	@Schema(description = "회원 정보가 마지막으로 바뀐 시각", example = "2026-11-10T09:30:00")
	LocalDateTime modifyDate
) {
	// 조회 쿼리(MemberRepository.findReplicasAfter)가 부른다. 탈퇴 닉네임은 닉네임 조회 API와 같은 방식이다
	public MemberReplicaDto(Long memberId, String nickname, MemberStatus status, LocalDateTime createDate,
		LocalDateTime modifyDate, Boolean sellerApproved) {
		this(memberId,
			status == MemberStatus.WITHDRAWN ? Member.WITHDRAWN_NICKNAME : nickname,
			Boolean.TRUE.equals(sellerApproved),
			status == MemberStatus.WITHDRAWN,
			createDate,
			modifyDate);
	}
}
