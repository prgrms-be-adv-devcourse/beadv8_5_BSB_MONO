package com.bukang.product.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * 종목의 기록증 규칙 (BUK-270). 판매자가 종목마다 정하고, member가 기록증을 판정할 때 쓴다.
 * 세 칸은 모두 있거나 규칙 자체가 없다 (11/17). 값 비교(equals)로 판매 시작 뒤 변경을 막는다.
 *
 * @param recognizedSince 이 날짜 이후(당일 포함) 대회의 기록만 인정한다
 * @param maxNetSeconds 기준 넷타임(초). 같으면 통과
 * @param allowedCourseTypes 인정 종목 구분
 */
public record RecordCertRule(LocalDate recognizedSince, int maxNetSeconds, Set<CourseType> allowedCourseTypes) {
}
