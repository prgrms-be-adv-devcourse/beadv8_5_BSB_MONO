package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산이 상품 서비스(B)에서 받아야 하는 대회 정보의 계약 (포트)
 * 정산 생성 배치는 상품 서비스에 대상 대회를 먼저 묻고, 그 대회의 판매 값을 주문 서비스에 묻는다 (PRO-214)
 * - 1차: 환불 마감일이 기간 안인 대회
 * - 최종: 대회일이 기간 안인 대회
 * 대회 이름도 함께 받아 정산 내역에 저장한다. 판매자 조회 때는 부르지 않으므로 상품 서비스가 멈춰도 정산 화면은 뜬다
 * 실제 어댑터(HTTP 호출)는 별도 이슈에서 만든다. 그때까지는 가짜 어댑터를 쓴다
 * 두 메서드 모두 배치가 놓친 날을 따라잡도록 하루가 아니라 기간으로 묻는다 (PRO-61과 같은 방식)
 */
public interface RacePort {
	/**
	 * 환불 마감일이 from ~ to(양 끝 포함)인 대회를 돌려준다. 1차 정산 생성 배치가 대상 대회를 고를 때 쓴다
	 */
	List<RaceSchedule> findRacesByRefundDeadline(LocalDate fromRefundDeadline, LocalDate toRefundDeadline);

	/**
	 * 대회일이 from ~ to(양 끝 포함)인 대회를 돌려준다. 최종 정산 생성 배치가 대상 대회를 고를 때 쓴다
	 */
	List<RaceSchedule> findRacesByRaceDate(LocalDate fromRaceDate, LocalDate toRaceDate);
}
