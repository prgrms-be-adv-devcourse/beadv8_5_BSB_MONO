package com.bukang.payout.dto;

import java.time.LocalDate;

/**
 * 지급 배치 한 번의 결과. dev 배치 실행 API 응답에 쓴다
 *
 * @param today   지급 예정일과 비교한 날짜
 * @param paid    지급 완료로 바꾼 정산 내역 수
 * @param failed  돈이 옮겨지지 않아 정산 예정으로 남긴 정산 내역 수. 다음 실행에서 다시 지급한다
 * @param unknown 정산금 이동 결과를 몰라 지급 중으로 남긴 정산 내역 수. 다음 실행에서 다시 요청한다
 */
public record PayoutPayResultDto(LocalDate today, int paid, int failed, int unknown) {
}
