package com.bukang.member.recordcert.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.recordcert.app.RecordCertFacade;
import com.bukang.member.recordcert.dto.MyRecordCertSubmissionDto;
import com.bukang.member.recordcert.dto.RecordCertSubmitRequestDto;
import com.bukang.member.recordcert.dto.RecordCertSubmittedDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "RecordCert", description = "기록증 제출, 내 기록증 제출 목록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/record-certs")
public class ApiV1RecordCertController {
	private final RecordCertFacade recordCertFacade;

	@PostMapping
	@Operation(operationId = "submitRecordCert", summary = "기록증 제출",
		description = "내 결제 완료 참가권에 기록증 사진과 확정 값(넷타임 · 대회일 · 종목)을 낸다. "
			+ "참가권 주인(order) · 사진을 올린 회원(file) · 종목 규칙(product)을 확인한 뒤 제출을 확인 중으로 저장하고 바로 답한다. "
			+ "대회 · 종목은 참가권 값으로 저장한다. 서버 확인과 판정은 나중에 하며 결과는 내 제출 목록에서 본다. "
			+ "같은 참가권에 확인 중 · 심사 필요 · 자동 승인 · 승인인 제출이 있으면 낼 수 없고, 반려 · 승인 불가 · 취소됨 뒤에는 다시 낼 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "제출 성공 (확인 중)",
			content = @Content(examples = @ExampleObject(value = RecordCertApiExamples.SUBMIT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 요청 본문 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = RecordCertApiExamples.INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = RecordCertApiExamples.INVALID_BODY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "남의 참가권 또는 남이 올린 사진",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "남의 참가권", value = RecordCertApiExamples.TICKET_FORBIDDEN),
				@ExampleObject(name = "남이 올린 사진", value = RecordCertApiExamples.FILE_FORBIDDEN)
			})),
		@ApiResponse(responseCode = "404", description = "없는 참가권 또는 없거나 삭제된 사진",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "없는 참가권", value = RecordCertApiExamples.TICKET_NOT_FOUND),
				@ExampleObject(name = "없는 사진", value = RecordCertApiExamples.FILE_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "409",
			description = "결제 완료가 아닌 참가권, 규칙이 없는 종목, 진행 중이거나 승인된 제출이 있음, 같은 참가권 제출을 처리 중",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "결제 완료가 아님", value = RecordCertApiExamples.TICKET_NOT_PAID),
				@ExampleObject(name = "규칙이 없는 종목", value = RecordCertApiExamples.RULE_NOT_FOUND),
				@ExampleObject(name = "진행 중인 제출", value = RecordCertApiExamples.SUBMISSION_IN_PROGRESS),
				@ExampleObject(name = "동시 제출", value = RecordCertApiExamples.SUBMIT_LOCKED)
			})),
		@ApiResponse(responseCode = "503", description = "주문 · 파일 · 상품 서비스나 Redis가 응답하지 않음 (아무것도 저장하지 않음)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RecordCertApiExamples.CHECK_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<RecordCertSubmittedDto>> submit(
		@LoginMemberId long memberId,
		@Valid @RequestBody RecordCertSubmitRequestDto request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "기록증을 제출했습니다.", recordCertFacade.submit(memberId, request)));
	}

	@GetMapping
	@Operation(operationId = "findMyRecordCerts", summary = "내 기록증 제출 목록",
		description = "내가 낸 기록증 제출을 최신순으로 모두 돌려준다(페이지 없음). 상태와 판정 사유만 보이고, "
			+ "칸별 서버 확인 결과는 보이지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (낸 제출이 없으면 빈 배열)",
			content = @Content(examples = @ExampleObject(value = RecordCertApiExamples.FIND_MINE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = RecordCertApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<List<MyRecordCertSubmissionDto>>> findMine(@LoginMemberId long memberId) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 기록증 제출 목록을 조회했습니다.", recordCertFacade.findMySubmissions(memberId)));
	}
}
