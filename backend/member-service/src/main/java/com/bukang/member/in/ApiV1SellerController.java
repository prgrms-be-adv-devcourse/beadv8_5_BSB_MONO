package com.bukang.member.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.SellerFacade;
import com.bukang.member.dto.MyPayoutAccountDto;
import com.bukang.member.dto.SellerApplyRequestDto;
import com.bukang.member.dto.SellerDto;
import com.bukang.member.dto.SellerPayoutAccountModifyRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Seller", description = "판매자 등록 신청, 내 신청 조회, 신청 결과 확인, 내 정산 계좌 조회·수정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/sellers")
public class ApiV1SellerController {
	private final SellerFacade sellerFacade;

	@PostMapping
	@Operation(summary = "판매자 등록 신청",
		description = "X-Member-Id 헤더의 회원이 판매자 등록을 신청한다. 사업자는 상호·사업자등록번호가 필수이고, "
			+ "비사업자가 보낸 상호·사업자등록번호는 버린다. 대기·승인 상태 신청이 있으면 막고, 반려만 있으면 새로 신청한다. "
			+ "정산 계좌는 오픈뱅킹 계좌실명조회로 확인한다. 사업자 명의는 사업자등록번호로 조회해 은행이 맞다고 답하면, "
			+ "본인 명의는 본인인증 생년월일로 조회해 예금주 이름이 본인인증 이름과 같으면(공백 무시) 통과한다. "
			+ "계좌 확인은 회원 한 명당 하루 5번까지 할 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "신청 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.APPLY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류, 예금주 정보 불일치 또는 본인인증 다시 필요",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = SellerApiExamples.APPLY_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = SellerApiExamples.INVALID_BODY),
				@ExampleObject(name = "예금주 정보 불일치", value = SellerApiExamples.ACCOUNT_NOT_VERIFIED),
				@ExampleObject(name = "본인인증 다시 필요 (본인 명의, 본인인증 해시 없음)",
					value = SellerApiExamples.IDENTITY_REVERIFY_REQUIRED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "대기 또는 승인 상태 신청이 있음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ALREADY_APPLIED))),
		@ApiResponse(responseCode = "429", description = "오늘 계좌 확인을 5번 넘게 함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ACCOUNT_VERIFY_TOO_MANY_REQUESTS))),
		@ApiResponse(responseCode = "503", description = "오픈뱅킹이 응답하지 않음 (시간 초과, 서버 오류, 설정 누락)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ACCOUNT_VERIFY_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<SellerDto>> apply(
		@LoginMemberId long memberId,
		@Valid @RequestBody SellerApplyRequestDto sellerApplyRequestDto
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(RsData.of(HttpStatus.CREATED, "판매자 등록을 신청했습니다.",
				sellerFacade.apply(memberId, sellerApplyRequestDto)));
	}

	@GetMapping("/me")
	@Operation(summary = "내 판매자 신청 조회",
		description = "X-Member-Id 헤더의 회원의 가장 최근 판매자 신청을 조회한다. 반려됐으면 반려 사유도 담는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.FIND_MINE_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MY_SELLER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<SellerDto>> findMine(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청 내역을 조회했습니다.", sellerFacade.findMine(memberId)));
	}

	@PostMapping("/me/result-check")
	@Operation(summary = "내 판매자 신청 결과 확인",
		description = "X-Member-Id 헤더의 회원이 가장 최근 판매자 신청의 승인·반려 결과를 봤다고 기록한다. "
			+ "이미 기록했으면 처음 시각을 그대로 두고 200이다. 심사 중인 신청은 기록하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "기록 성공 (이미 기록했어도 200)",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.CHECK_RESULT_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.MY_SELLER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "가장 최근 신청이 아직 심사 중",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.NOT_REVIEWED)))
	})
	public ResponseEntity<RsData<SellerDto>> checkMyResult(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "판매자 신청 결과를 확인했습니다.", sellerFacade.checkMyResult(memberId)));
	}

	@GetMapping("/me/payout-account")
	@Operation(summary = "내 정산 계좌 조회",
		description = "X-Member-Id 헤더의 회원이 자기 정산 계좌를 조회한다. 승인된 판매자만 볼 수 있다. "
			+ "은행 코드, 예금주, 계좌번호 끝 4자리만 돌려주고 전체 계좌번호는 돌려주지 않는다. "
			+ "계좌가 없으면 registered가 false이고, 이때 정산 지급은 보류된다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공 (계좌가 없으면 registered false)",
			content = @Content(examples = {
				@ExampleObject(name = "계좌 있음", value = SellerApiExamples.MY_PAYOUT_ACCOUNT_SUCCESS),
				@ExampleObject(name = "계좌 없음", value = SellerApiExamples.MY_PAYOUT_ACCOUNT_EMPTY)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인되지 않은 판매자 (심사 중 또는 반려)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 없음", value = SellerApiExamples.MEMBER_NOT_FOUND),
				@ExampleObject(name = "판매자 신청 내역 없음", value = SellerApiExamples.MY_SELLER_NOT_FOUND)
			}))
	})
	public ResponseEntity<RsData<MyPayoutAccountDto>> findMyPayoutAccount(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "정산 계좌를 조회했습니다.", sellerFacade.findMyPayoutAccount(memberId)));
	}

	@PutMapping("/me/payout-account")
	@Operation(summary = "내 정산 계좌 등록·수정",
		description = "X-Member-Id 헤더의 승인된 판매자가 정산 계좌를 등록하거나 바꾼다. 관리자 승인은 거치지 않는다. "
			+ "판매자 신청과 같은 오픈뱅킹 계좌실명조회를 통과해야 저장하고, 통과하지 못하면 기존 계좌를 그대로 둔다. "
			+ "사업자 명의는 판매자 신청서의 사업자등록번호로, 본인 명의는 본인인증 생년월일로 조회한다. "
			+ "계좌 확인은 회원 한 명당 하루 5번까지 할 수 있고, 신청 때 쓴 횟수와 합쳐 센다. "
			+ "응답에는 전체 계좌번호 없이 끝 4자리만 담는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장 성공",
			content = @Content(examples = @ExampleObject(value = SellerApiExamples.MODIFY_PAYOUT_ACCOUNT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류, 비사업자의 사업자 명의 선택, "
			+ "예금주 정보 불일치 또는 본인인증 다시 필요",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = SellerApiExamples.APPLY_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = SellerApiExamples.INVALID_BODY),
				@ExampleObject(name = "비사업자의 사업자 명의 선택", value = SellerApiExamples.ACCOUNT_HOLDER_TYPE_INVALID),
				@ExampleObject(name = "예금주 정보 불일치", value = SellerApiExamples.ACCOUNT_NOT_VERIFIED),
				@ExampleObject(name = "본인인증 다시 필요 (본인 명의, 본인인증 해시 없음)",
					value = SellerApiExamples.IDENTITY_REVERIFY_REQUIRED)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "승인되지 않은 판매자 (심사 중 또는 반려)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 판매자 신청 내역 없음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 없음", value = SellerApiExamples.MEMBER_NOT_FOUND),
				@ExampleObject(name = "판매자 신청 내역 없음", value = SellerApiExamples.MY_SELLER_NOT_FOUND)
			})),
		@ApiResponse(responseCode = "429", description = "오늘 계좌 확인을 5번 넘게 함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ACCOUNT_VERIFY_TOO_MANY_REQUESTS))),
		@ApiResponse(responseCode = "503", description = "오픈뱅킹이 응답하지 않음 (시간 초과, 서버 오류, 설정 누락)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = SellerApiExamples.ACCOUNT_VERIFY_UNAVAILABLE)))
	})
	public ResponseEntity<RsData<MyPayoutAccountDto>> modifyMyPayoutAccount(
		@LoginMemberId long memberId,
		@Valid @RequestBody SellerPayoutAccountModifyRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "정산 계좌를 저장했습니다.", sellerFacade.modifyMyPayoutAccount(memberId, request)));
	}
}
