package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;
import com.bukang.member.app.MemberDltUseCase;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.dto.MyRecordCertSubmissionDto;
import com.bukang.member.recordcert.dto.RecordCertSubmitRequestDto;
import com.bukang.member.recordcert.dto.RecordCertSubmittedDto;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 참가자 기록증 API (BUK-274)
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCertFacade {
	private final RecordCertSubmitUseCase recordCertSubmitUseCase;
	private final RecordCertCheckResultUseCase recordCertCheckResultUseCase;
	private final RecordCertSubmissionRepository recordCertSubmissionRepository;
	private final MemberDltUseCase memberDltUseCase;
	private final JsonConverter jsonConverter;

	// 트랜잭션은 UseCase가 저장할 때만 연다 (다른 서비스를 부르는 동안 DB 연결을 잡지 않게)
	public RecordCertSubmittedDto submit(long memberId, RecordCertSubmitRequestDto request) {
		return recordCertSubmitUseCase.submit(memberId, request);
	}

	@Transactional(readOnly = true)
	public List<MyRecordCertSubmissionDto> findMySubmissions(long memberId) {
		return recordCertSubmissionRepository.findByMemberIdOrderByIdDesc(memberId).stream()
			.map(MyRecordCertSubmissionDto::from)
			.toList();
	}

	// 서버 확인 결과 반영 (BUK-275). 트랜잭션은 저장할 때만 연다 (product를 부르는 동안 DB 연결을 잡지 않게)
	public void applyCheckResult(RecordCertCheckedEvent result) {
		recordCertCheckResultUseCase.apply(result);
	}

	// 실패 토픽으로 간 서버 확인 결과 (BUK-275): 실패를 남기고, 그 제출이 아직 확인 중이면 심사 필요로 넘긴다
	// 본문을 읽을 수 없으면 제출을 찾을 수 없어 실패만 남긴다(Slack으로 알림)
	@Transactional
	public void handleCheckResultDlt(KafkaDltMessage message) {
		memberDltUseCase.record(message);
		RecordCertCheckedEvent result;
		try {
			result = jsonConverter.fromJson(message.payload(), RecordCertCheckedEvent.class);
		} catch (IllegalArgumentException broken) {
			log.error("읽을 수 없는 서버 확인 결과라 심사 필요로 넘기지 못했습니다. payload={}", message.payload(), broken);
			return;
		}
		recordCertSubmissionRepository.findById(result.submissionId())
			.filter(submission -> submission.getStatus() == SubmissionStatus.CHECKING)
			.ifPresentOrElse(submission -> submission.giveUpCheck(LocalDateTime.now()),
				() -> log.info("확인 중이 아닌 제출이라 실패 토픽의 결과를 넘기지 않습니다. submissionId={}",
					result.submissionId()));
	}
}
