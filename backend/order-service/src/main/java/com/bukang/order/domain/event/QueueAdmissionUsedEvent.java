package com.bukang.order.domain.event;

/**
 * 대기열 입장이 더 필요 없어졌다 (CRU-158). 개인은 결제를 끝냈을 때, 파티는 파티장이 결제 기간을 열었을 때 발행한다
 * QueueAdmissionReleaseListener가 커밋 뒤 대기열에 입장 해제를 부른다. 15분 동안 다음 사람의 자리를 막지 않으려고
 *
 * @param memberId     입장한 회원 ID (파티는 파티장)
 * @param raceId       대회 ID
 * @param raceCourseId 종목 ID
 * @param party        파티 대기열 입장이면 true
 */
public record QueueAdmissionUsedEvent(long memberId, long raceId, long raceCourseId, boolean party) {
}
