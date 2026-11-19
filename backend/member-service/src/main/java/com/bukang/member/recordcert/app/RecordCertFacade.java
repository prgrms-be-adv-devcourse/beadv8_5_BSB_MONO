package com.bukang.member.recordcert.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.recordcert.dto.MyRecordCertSubmissionDto;
import com.bukang.member.recordcert.dto.RecordCertSubmitRequestDto;
import com.bukang.member.recordcert.dto.RecordCertSubmittedDto;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;

import lombok.RequiredArgsConstructor;

// 참가자 기록증 API (BUK-274)
@Service
@RequiredArgsConstructor
public class RecordCertFacade {
	private final RecordCertSubmitUseCase recordCertSubmitUseCase;
	private final RecordCertSubmissionRepository recordCertSubmissionRepository;

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
}
