package com.bukang.product.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

/**
 * 크루 자리 확보 요청의 자리 목록이 이미 잡힌 자리와 맞지 않을 때
 * - 이미 크루 자리를 잡은 파티가 처음과 다른 자리 목록으로 다시 확보하려 함
 *   같은 파티로 다시 부르면 처음 결과를 돌려주는데(멱등), 목록이 다르면 처음 결과가 이 요청의 답이 아니다
 *   예) 잡은 뒤 주문이 롤백되고 그 사이 파티원이 바뀜 → 나간 사람 자리는 남고 새로 온 사람 자리는 없다 (주문 CrewSeatHoldPort 주석)
 * - 다른 파티나 개인으로 이미 자리를 잡은 참가 신청이 목록에 섞여 있음 (참가 신청 1건 = 자리 1석)
 * - 자리 목록의 종목이 요청한 대회의 종목이 아님
 */
public class CrewHoldMismatchException extends BusinessException {
	public CrewHoldMismatchException() {
		this("이미 다른 자리 목록으로 확보한 파티입니다.");
	}

	private CrewHoldMismatchException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

	public static CrewHoldMismatchException participationTaken() {
		return new CrewHoldMismatchException("이미 자리를 확보한 참가 신청이 들어 있습니다.");
	}

	// 자리 목록의 종목이 요청한 대회의 종목이 아닐 때 (없는 종목은 RaceCourseNotFoundException 404)
	public static CrewHoldMismatchException courseNotInRace() {
		return new CrewHoldMismatchException("이 대회의 종목이 아닙니다.");
	}
}
