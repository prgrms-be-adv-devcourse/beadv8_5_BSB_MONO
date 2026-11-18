package com.bukang.member.recordcert.domain;

import java.time.LocalDate;

/**
 * 참가자가 확정한 기록증 값. 비어 있는 칸은 null
 *
 * @param netSeconds 넷타임(초)
 * @param bib 배번 (판정 · 서버 확인에 쓰지 않음)
 */
public record CertValues(Integer netSeconds, LocalDate raceDate, CourseClass courseClass, String bib) {
}
