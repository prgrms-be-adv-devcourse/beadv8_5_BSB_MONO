package com.bukang.member.recordcert.dto;

import com.bukang.member.recordcert.domain.SubmissionStatus;

// 기록증 제출 응답. 서버 확인은 나중에 하므로 늘 확인 중(CHECKING)이다
public record RecordCertSubmittedDto(long submissionId, SubmissionStatus status) {
}
