package com.bukang.member.domain.exception;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

/**
 * 탈퇴하고 정한 날수(기본 30일)가 지나지 않은 사람이 다시 가입하려 한다 (BUK-33, 11/2 지훈 님 결정)
 * 응답 data에 다시 가입할 수 있는 날을 담아, 화면이 「○월 ○일부터 가입할 수 있어요」를 보여 준다 (MemberExceptionHandler)
 */
@Getter
public class RejoinTooSoonException extends BusinessException {
	public static final String CODE = "MEMBER_REJOIN_TOO_SOON";

	private final LocalDate rejoinableDate;
	private final int blockDays;

	public RejoinTooSoonException(LocalDate rejoinableDate, int blockDays) {
		super(HttpStatus.CONFLICT, CODE, "탈퇴하고 %d일이 지나야 다시 가입할 수 있습니다. %d월 %d일부터 가입할 수 있습니다."
			.formatted(blockDays, rejoinableDate.getMonthValue(), rejoinableDate.getDayOfMonth()));
		this.rejoinableDate = rejoinableDate;
		this.blockDays = blockDays;
	}
}
