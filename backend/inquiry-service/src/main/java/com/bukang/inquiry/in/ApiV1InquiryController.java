package com.bukang.inquiry.in;

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
import com.bukang.inquiry.app.InquiryFacade;
import com.bukang.inquiry.app.InquiryUseCase;
import com.bukang.inquiry.dto.InquiryAnswerRequestDto;
import com.bukang.inquiry.dto.InquiryCreateRequestDto;
import com.bukang.inquiry.dto.InquiryDto;
import com.bukang.inquiry.dto.InquiryPageDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "Inquiry", description = "대회 문의 남기기, 내 문의 조회, 판매자·관리자 문의 조회와 답변 API")
@RestController
@RequestMapping("/api/v1/inquiry")
@RequiredArgsConstructor
public class ApiV1InquiryController {
	private static final int MAX_PAGE_SIZE = 100;

	private final InquiryFacade inquiryFacade;
	private final InquiryUseCase inquiryUseCase;

	@PostMapping("/inquiries")
	@Operation(summary = "문의 남기기",
		description = "대회에 문의를 남긴다. 종류로 담당(판매자·관리자)이 정해지고, 판매자 담당이면 그 대회 판매자에게 알림이 간다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "문의 등록 성공",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.CREATE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 오류(빈 값, 글자 수 초과, 없는 종류), 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 오류", value = InquiryApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 대회",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.RACE_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "상품 서비스에 연결하지 못해 판매자를 알 수 없음 (문의는 저장하지 않는다)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.PRODUCT_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<InquiryDto>> create(@LoginMemberId long memberId,
		@Valid @RequestBody InquiryCreateRequestDto request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "문의를 남겼습니다.", inquiryFacade.create(memberId, request)));
	}

	@GetMapping("/inquiries/me")
	@Operation(summary = "내 문의 목록", description = "내가 남긴 문의를 최신순으로 준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = InquiryApiExamples.INVALID_PAGE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED)))
	})
	public RsData<InquiryPageDto> findMine(@LoginMemberId long memberId, @RequestParam(defaultValue = "0")
	@Parameter(description = "페이지 번호 (0부터)", example = "0")
	@Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@RequestParam(defaultValue = "20")
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size) {
		return RsData.of(HttpStatus.OK, "문의 목록을 조회했습니다.", inquiryUseCase.findMine(memberId, page, size));
	}

	@GetMapping("/inquiries/{inquiryId}")
	@Operation(summary = "내 문의 상세", description = "내가 남긴 문의 하나와 답변을 준다. 남의 문의는 없는 문의와 같이 404다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.FIND_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "문의 ID 형식 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "문의 ID 형식 오류", value = InquiryApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 문의이거나 다른 회원의 문의",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.NOT_FOUND)))
	})
	public RsData<InquiryDto> findOne(@LoginMemberId long memberId,
		@Parameter(description = "문의 ID", example = "12") @PathVariable long inquiryId) {
		return RsData.of(HttpStatus.OK, "문의를 조회했습니다.", inquiryUseCase.findMineOne(memberId, inquiryId));
	}

	@GetMapping("/seller/inquiries")
	@Operation(summary = "판매자 문의 목록",
		description = "내 대회에 달린 판매자 담당 문의를 최신순으로 준다. 문의를 만들 때 저장한 판매자 회원이 나인 문의만 보인다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = InquiryApiExamples.INVALID_PAGE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED)))
	})
	public RsData<InquiryPageDto> findForSeller(@LoginMemberId long memberId, @RequestParam(defaultValue = "0")
	@Parameter(description = "페이지 번호 (0부터)", example = "0")
	@Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@RequestParam(defaultValue = "20")
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size) {
		return RsData.of(HttpStatus.OK, "문의 목록을 조회했습니다.", inquiryUseCase.findForSeller(memberId, page, size));
	}

	@PostMapping("/seller/inquiries/{inquiryId}/answer")
	@Operation(summary = "판매자 답변",
		description = "내 대회의 판매자 담당 문의에 답한다. 답변은 한 번만 달 수 있고, 답하면 문의한 회원에게 알림이 간다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "답변 성공",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.ANSWER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 오류(빈 값, 글자 수 초과), 문의 ID 형식 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 오류", value = InquiryApiExamples.INVALID_BODY),
				@ExampleObject(name = "문의 ID 형식 오류", value = InquiryApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "없는 문의, 다른 판매자 대회의 문의, 관리자 담당 문의",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 답변한 문의 (동시에 답해도 하나만 저장)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.ALREADY_ANSWERED)))
	})
	public RsData<InquiryDto> answerBySeller(@LoginMemberId long memberId,
		@Parameter(description = "문의 ID", example = "12") @PathVariable long inquiryId,
		@Valid @RequestBody InquiryAnswerRequestDto request) {
		return RsData.of(HttpStatus.OK, "답변을 남겼습니다.",
			inquiryUseCase.answerBySeller(memberId, inquiryId, request.content()));
	}

	@GetMapping("/admin/inquiries")
	@Operation(summary = "관리자 문의 목록",
		description = "담당과 상관없이 모든 문의를 최신순으로 준다. 관리자만 쓴다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 목록)",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.LIST_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "페이지 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "페이지 크기 초과", value = InquiryApiExamples.INVALID_PAGE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 관리자인지 알 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.MEMBER_UNAVAILABLE)))
	})
	public RsData<InquiryPageDto> findForAdmin(@LoginMemberId long memberId, @RequestParam(defaultValue = "0")
	@Parameter(description = "페이지 번호 (0부터)", example = "0")
	@Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
		@RequestParam(defaultValue = "20")
		@Parameter(description = "페이지 크기 (1~100)", example = "20")
		@Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = MAX_PAGE_SIZE, message = "한 번에 100개까지 조회할 수 있습니다.") int size) {
		return RsData.of(HttpStatus.OK, "문의 목록을 조회했습니다.", inquiryFacade.findForAdmin(memberId, page, size));
	}

	@PostMapping("/admin/inquiries/{inquiryId}/answer")
	@Operation(summary = "관리자 답변",
		description = "관리자 담당 문의에 답한다. 판매자 담당 문의는 답할 수 없다. 답변은 한 번만 달 수 있고, 답하면 문의한 회원에게 알림이 간다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "답변 성공",
			content = @Content(examples = @ExampleObject(value = InquiryApiExamples.ANSWER_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 오류(빈 값, 글자 수 초과), 문의 ID 형식 오류, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 오류", value = InquiryApiExamples.INVALID_BODY),
				@ExampleObject(name = "문의 ID 형식 오류", value = InquiryApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID 오류", value = InquiryApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "관리자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.NOT_ADMIN))),
		@ApiResponse(responseCode = "404", description = "없는 문의이거나 판매자 담당 문의",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 답변한 문의 (동시에 답해도 하나만 저장)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.ALREADY_ANSWERED))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 관리자인지 알 수 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = InquiryApiExamples.MEMBER_UNAVAILABLE)))
	})
	public RsData<InquiryDto> answerByAdmin(@LoginMemberId long memberId,
		@Parameter(description = "문의 ID", example = "12") @PathVariable long inquiryId,
		@Valid @RequestBody InquiryAnswerRequestDto request) {
		return RsData.of(HttpStatus.OK, "답변을 남겼습니다.",
			inquiryFacade.answerByAdmin(memberId, inquiryId, request.content()));
	}
}
