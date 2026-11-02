package com.bukang.member.in;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.dto.EmailAvailabilityRequestDto;
import com.bukang.member.dto.EmailAvailabilityResponseDto;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberModifyRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "이메일 중복 확인, 내 정보 조회, 내 정보 수정, 회원 탈퇴 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/member/members")
public class ApiV1MemberController {
	private final MemberFacade memberFacade;

	// 가입 화면이 이메일 단계에서 중복을 알려 주는 용도. 토큰 없이 부른다(게이트웨이 공개 주소). 중복도 정상 흐름이라 409가 아닌 200 (BE-023)
	@GetMapping("/email-availability")
	@Operation(summary = "이메일 사용 가능 여부 확인",
		description = "이메일이 아직 가입되지 않았으면 available=true, 이미 가입됐으면 false를 돌려준다. 로그인 없이 부를 수 있다. "
			+ "탈퇴한 회원의 이메일은 비워지므로 다시 쓸 수 있다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확인 성공 (이미 쓰는 이메일이어도 200)",
			content = @Content(examples = @ExampleObject(value = MemberApiExamples.EMAIL_AVAILABILITY_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "이메일 누락, 형식 오류 또는 255자 초과",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.EMAIL_AVAILABILITY_INVALID_INPUT)))
	})
	public ResponseEntity<RsData<EmailAvailabilityResponseDto>> checkEmailAvailability(
		@Valid @ModelAttribute @ParameterObject EmailAvailabilityRequestDto emailAvailabilityRequestDto
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "이메일 사용 가능 여부를 확인했습니다.",
				memberFacade.checkEmailAvailability(emailAvailabilityRequestDto.email())));
	}

	// 새로고침 등으로 화면이 로그인한 회원 정보를 잃었을 때 Access Token만으로 다시 불러오는 용도
	@GetMapping("/me")
	@Operation(summary = "내 정보 조회",
		description = "게이트웨이가 Access Token에서 꺼내 넣은 회원 ID(X-Member-Id 헤더)로 로그인한 회원의 정보를 조회한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(examples = @ExampleObject(value = MemberApiExamples.FIND_ME_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.MEMBER_NOT_FOUND)))
	})
	public ResponseEntity<RsData<MemberDto>> findMe(
		@LoginMemberId long memberId
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 정보를 조회했습니다.", memberFacade.findMe(memberId)));
	}

	// 수정 화면은 저장할 때 항상 4칸을 다 보내므로 PUT으로 통째로 바꾼다. 대상은 X-Member-Id로만 정한다 (BE-012)
	@PutMapping("/me")
	@Operation(summary = "내 정보 수정",
		description = "X-Member-Id 헤더의 회원의 이름, 생년월일, 휴대폰 번호, 닉네임을 바꾼다. "
			+ "다른 회원이 쓰는 닉네임·휴대폰 번호인지 검사하고(내 행은 제외, 휴대폰은 하이픈 유무 무관), "
			+ "4칸이 모두 그대로면 저장하지 않는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공 (바뀐 게 없어도 200)",
			content = @Content(examples = @ExampleObject(value = MemberApiExamples.MODIFY_ME_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류 또는 만 14세 미만",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = MemberApiExamples.MODIFY_ME_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = MemberApiExamples.INVALID_BODY),
				@ExampleObject(name = "만 14세 미만", value = MemberApiExamples.MODIFY_ME_UNDER_AGE)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "다른 회원이 쓰는 닉네임 또는 휴대폰 번호",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "닉네임 중복", value = MemberApiExamples.DUPLICATE_NICKNAME),
				@ExampleObject(name = "휴대폰 번호 중복", value = MemberApiExamples.DUPLICATE_PHONE)
			}))
	})
	public ResponseEntity<RsData<MemberDto>> modifyMe(
		@LoginMemberId long memberId,
		@Valid @RequestBody MemberModifyRequestDto memberModifyRequestDto
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "내 정보를 수정했습니다.",
				memberFacade.modifyMe(memberId, memberModifyRequestDto)));
	}

	// 탈퇴는 남은 예치금·끝나지 않은 참가권이 없을 때만 바로 끝내고, 줄은 남긴 채 개인정보 칸을 비운다 (BE-022)
	@DeleteMapping("/me")
	@Operation(summary = "회원 탈퇴",
		description = "X-Member-Id 헤더의 회원을 탈퇴시킨다. 예치금 서비스와 주문 서비스에 남은 예치금과 끝나지 않은 참가권"
			+ "(취소·환불되지 않았고 대회 날짜가 오늘 이후)을 물어, 하나라도 있으면 409로 막는다. "
			+ "확인에 실패하면 탈퇴하지 않고 503으로 답한다. 탈퇴하면 개인정보 칸을 비우고 모든 기기의 Refresh Token을 지운다. "
			+ "이미 발급된 Access Token은 만료될 때까지 남는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "탈퇴 성공",
			content = @Content(examples = @ExampleObject(value = MemberApiExamples.WITHDRAW_ME_SUCCESS))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음 (로그인하지 않은 요청)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "404", description = "헤더의 회원이 없거나 이미 탈퇴함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.WITHDRAW_ME_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "남은 예치금 또는 끝나지 않은 참가권이 있음",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "예치금 남음", value = MemberApiExamples.WITHDRAW_CASH_LEFT),
				@ExampleObject(name = "참가권 남음", value = MemberApiExamples.WITHDRAW_TICKET_LEFT)
			})),
		@ApiResponse(responseCode = "503", description = "예치금·참가권 확인 실패 (탈퇴하지 않음)",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = MemberApiExamples.WITHDRAW_CHECK_FAILED)))
	})
	public ResponseEntity<RsData<Void>> withdrawMe(
		@LoginMemberId long memberId
	) {
		memberFacade.withdrawMe(memberId);
		return ResponseEntity.ok().body(RsData.of(HttpStatus.OK, "탈퇴했습니다.", null));
	}
}
