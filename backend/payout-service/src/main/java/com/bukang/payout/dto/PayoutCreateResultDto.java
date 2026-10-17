package com.bukang.payout.dto;

import java.time.LocalDate;

/**
 * 정산 대상일 배치 한 번의 결과. 실행 로그와 dev 배치 실행 API 응답에 쓴다
 *
 * @param fromRaceDate 이번 실행이 본 대회일 시작 (되돌아보기, 양 끝 포함)
 * @param toRaceDate   이번 실행이 본 대회일 끝 (어제)
 * @param created      새로 만든 정산 내역 수
 * @param skipped      이미 있어 건너뛴 정산 내역 수
 * @param failed       실패해 롤백한 정산 내역 수 (다음 실행이 다시 시도한다)
 */
public record PayoutCreateResultDto(LocalDate fromRaceDate, LocalDate toRaceDate, int created, int skipped,
	int failed) {
}
