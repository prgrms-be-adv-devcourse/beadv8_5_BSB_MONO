package com.bukang.member.recordcert.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * 기록증 규칙: 판매자가 종목마다 정한다 (원본은 product-service)
 *
 * @param recognizedSince 이 날짜 이후(당일 포함) 대회의 기록만 인정
 * @param maxNetSeconds 기준 넷타임(초). 같으면 통과
 * @param allowedClasses 인정 종목 구분
 */
public record CourseRule(LocalDate recognizedSince, int maxNetSeconds, Set<CourseClass> allowedClasses) {
}
