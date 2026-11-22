package com.bukang.member.recordcert.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.recordcert.app.RecordCertSellerFacade;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.dto.RecordCertDenyRequestDto;
import com.bukang.member.recordcert.dto.RecordCertReviewCountDto;
import com.bukang.member.recordcert.dto.SellerRecordCertDetailDto;
import com.bukang.member.recordcert.dto.SellerRecordCertSubmissionDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "SellerRecordCert", description = "판매자 기록증 제출 목록, 제출 상세, 승인, 승인 불가, 심사 필요 건수 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/seller")
public class ApiV1SellerRecordCertController {
	private final RecordCertSellerFacade recordCertSellerFacade;

	@GetMapping("/races/{raceId}/record-certs")
	@Operation(operationId = "findRaceRecordCerts", summary = "대회 기록증 제출 목록",
		description = "내 대회의 기록증 제출을 최신순으로 모두 돌려준다(페이지 없음). 상태와 서버 확인 결과(네 칸 모두 맞음 여부)로 거른다. "
			+ "제출이 한 건도 없는 대회는 빈 배열이고, 제출이 있는 남의 대회는 403이다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (제출이 없거나 거른 결과가 없으면 빈 배열)",
			content = @Content(examples = @ExampleObject(value = SellerRecordCertApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "대회 ID · 상태 · 서버 확인 결과 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 남의 대회",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 아님", value = SellerRecordCertApiExamples.NOT_SELLER),
				@ExampleObject(name = "남의 대회", value = SellerRecordCertApiExamples.NOT_OWNER)
			}))
	})
	public ResponseEntity<RsData<List<SellerRecordCertSubmissionDto>>> findRaceSubmissions(
		@LoginMemberId long memberId,
		@Parameter(description = "대회 ID", example = "1") @PathVariable long raceId,
		@Parameter(description = "제출 상태. 없으면 모두", example = "NEEDS_REVIEW")
		@RequestParam(required = false) SubmissionStatus status,
		@Parameter(description = "true면 네 칸 모두 맞음, false면 하나라도 다름 · 읽지 못함(확인 중 포함). 없으면 모두",
			example = "false")
		@RequestParam(required = false) Boolean serverCheckMatched
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "대회의 기록증 제출 목록을 조회했습니다.",
			recordCertSellerFacade.findRaceSubmissions(memberId, raceId, status, serverCheckMatched)));
	}

	@GetMapping("/record-certs/{submissionId}")
	@Operation(operationId = "findSellerRecordCert", summary = "기록증 제출 상세",
		description = "내 대회의 제출 하나를 원본 사진 보기 주소(30분 유효), 읽을 부분, 확정 값, 판정 사유, 칸별 서버 확인 결과와 함께 돌려준다. "
			+ "소유를 확인한 뒤에만 파일 서비스에서 보기 주소를 받는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerRecordCertApiExamples.DETAIL_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "제출 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 남의 대회 제출",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 아님", value = SellerRecordCertApiExamples.NOT_SELLER),
				@ExampleObject(name = "남의 대회", value = SellerRecordCertApiExamples.NOT_OWNER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 제출",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "파일 서비스가 사진 보기 주소를 주지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.FILE_VIEW_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerRecordCertDetailDto>> findDetail(
		@LoginMemberId long memberId,
		@Parameter(description = "제출 ID", example = "31") @PathVariable long submissionId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "기록증 제출을 조회했습니다.",
			recordCertSellerFacade.findDetail(memberId, submissionId)));
	}

	@PostMapping("/record-certs/{submissionId}/approve")
	@Operation(operationId = "approveRecordCert", summary = "기록증 제출 승인",
		description = "내 대회의 심사 필요 제출을 승인으로 정하고 심사 시각을 남긴다. 심사 필요가 아니면 409로 막아 끝난 결과가 뒤집히지 않게 한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "승인 성공",
			content = @Content(examples = @ExampleObject(value = SellerRecordCertApiExamples.APPROVE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "제출 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.INVALID_VALUE_TYPE))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 남의 대회 제출",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 아님", value = SellerRecordCertApiExamples.NOT_SELLER),
				@ExampleObject(name = "남의 대회", value = SellerRecordCertApiExamples.NOT_OWNER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 제출",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "심사 필요가 아닌 제출 (이미 심사함 포함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_REVIEWABLE)))
	})
	public ResponseEntity<RsData<SellerRecordCertSubmissionDto>> approve(
		@LoginMemberId long memberId,
		@Parameter(description = "제출 ID", example = "31") @PathVariable long submissionId
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "기록증 제출을 승인했습니다.",
			recordCertSellerFacade.approve(memberId, submissionId)));
	}

	@PostMapping("/record-certs/{submissionId}/deny")
	@Operation(operationId = "denyRecordCert", summary = "기록증 제출 승인 불가",
		description = "내 대회의 심사 필요 제출을 승인 불가로 정한다. 사유는 목록에서 하나 고르고 메모(200자 이하)는 선택이다. "
			+ "참가자는 내 제출 목록에서 사유를 보고 같은 참가권으로 다시 낼 수 있다. 심사 필요가 아니면 409다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "승인 불가 성공",
			content = @Content(examples = @ExampleObject(value = SellerRecordCertApiExamples.DENY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "사유 없음, 메모 200자 초과, 요청 본문 형식 오류, 제출 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "사유 없음", value = SellerRecordCertApiExamples.INVALID_INPUT),
				@ExampleObject(name = "메모 200자 초과", value = SellerRecordCertApiExamples.INVALID_MEMO),
				@ExampleObject(name = "요청 본문 형식 오류", value = SellerRecordCertApiExamples.INVALID_BODY),
				@ExampleObject(name = "제출 ID 형식 오류", value = SellerRecordCertApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님 또는 남의 대회 제출",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "판매자 아님", value = SellerRecordCertApiExamples.NOT_SELLER),
				@ExampleObject(name = "남의 대회", value = SellerRecordCertApiExamples.NOT_OWNER)
			})),
		@ApiResponse(responseCode = "404", description = "없는 제출",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "심사 필요가 아닌 제출 (이미 심사함 포함)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_REVIEWABLE)))
	})
	public ResponseEntity<RsData<SellerRecordCertSubmissionDto>> deny(
		@LoginMemberId long memberId,
		@Parameter(description = "제출 ID", example = "31") @PathVariable long submissionId,
		@Valid @RequestBody RecordCertDenyRequestDto request
	) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "기록증 제출을 승인 불가로 정했습니다.",
			recordCertSellerFacade.deny(memberId, submissionId, request.reason(), request.memo())));
	}

	@GetMapping("/record-certs/review-count")
	@Operation(operationId = "countRecordCertReviews", summary = "심사 필요 기록증 제출 수",
		description = "내 대회 전체의 심사 필요 제출 수와 대회별 건수를 돌려준다. 제출에 저장된 판매자 회원 ID로 센다. "
			+ "대시보드 처리할 일 카드와 AI 브리핑이 부른다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerRecordCertApiExamples.COUNT_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인된 판매자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerRecordCertApiExamples.NOT_SELLER)))
	})
	public ResponseEntity<RsData<RecordCertReviewCountDto>> countWaiting(@LoginMemberId long memberId) {
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "심사 필요 기록증 제출 수를 조회했습니다.",
			recordCertSellerFacade.countWaiting(memberId)));
	}
}
