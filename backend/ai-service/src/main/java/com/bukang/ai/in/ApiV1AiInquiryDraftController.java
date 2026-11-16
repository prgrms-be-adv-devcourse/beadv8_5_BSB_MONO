package com.bukang.ai.in;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.ai.app.AiInquiryDraftUseCase;
import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.dto.AiInquiryDraftDto;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Ai", description = "판매자·관리자 센터의 크루런 AI 질문, 새 대화, 문의 답변 초안 API")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ApiV1AiInquiryDraftController {
	private final AiInquiryDraftUseCase aiInquiryDraftUseCase;

	// 화면 종류는 seller·admin, 문의 ID는 18자리까지 숫자만 맞는 주소가 있다 (아니면 404. long을 넘는 수가 500이 되지 않게)
	@PostMapping("/{screen:seller|admin}/inquiries/{inquiryId:\\d{1,18}}/draft")
	@Operation(summary = "문의 답변 초안 만들기",
		description = "그 화면의 역할을 확인한 뒤, 문의 서비스에서 문의와 주문 정보를 읽어 답변 초안을 만든다. 보내지는 않고 저장하지도 않는다. "
			+ "판매자 화면은 자기 대회의 판매자 담당 문의만, 관리자 화면은 모든 문의를 쓸 수 있다(문의 서비스가 확인). "
			+ "문의 종류에 맞는 정해진 정책(docs/policy에서 옮긴 요약)을 함께 보내, 정책에 있는 일반 규칙은 그대로 안내한다. "
			+ "자료로 채우지 못한 곳은 초안에 [확인 필요: 무엇]으로 비워 두고 missing에 안내를 준다. "
			+ "references(참고한 정보: 주문, 보낸 정책 출처 「정책 N장 …」, 용어)와 asOf(조회 시각)는 모델이 아니라 코드가 붙인다. "
			+ "회원 닉네임은 모델에 보내지 않고, 본문 속 휴대폰·이메일·계좌 번호는 가려서 보낸다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "초안 만듦",
			content = @Content(examples = @ExampleObject(value = AiApiExamples.DRAFT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "그 화면의 역할이 없는 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.FORBIDDEN))),
		@ApiResponse(responseCode = "404", description = "없거나 지운 문의, 다른 판매자 대회의 문의, 판매자 화면에서 연 관리자 담당 문의",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.INQUIRY_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "이미 답변한 문의",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.INQUIRY_ALREADY_ANSWERED))),
		@ApiResponse(responseCode = "503", description = "회원·문의 서비스에 연결하지 못함, 또는 모델에서 쓸 수 있는 초안을 받지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스", value = AiApiExamples.MEMBER_UNAVAILABLE),
				@ExampleObject(name = "문의 서비스", value = AiApiExamples.INQUIRY_UNAVAILABLE),
				@ExampleObject(name = "모델", value = AiApiExamples.MODEL_UNAVAILABLE)
			}))
	})
	public RsData<AiInquiryDraftDto> draft(
		@LoginMemberId long memberId,
		@Parameter(description = "화면 종류 (seller: 판매자 센터, admin: 관리자 센터)", example = "admin",
			schema = @Schema(allowableValues = {"seller", "admin"}))
		@PathVariable String screen,
		@Parameter(description = "문의 ID", example = "12") @PathVariable long inquiryId
	) {
		AiScreen aiScreen = AiScreen.valueOf(screen.toUpperCase(Locale.ROOT));
		return RsData.of(HttpStatus.OK, "답변 초안을 만들었습니다.",
			aiInquiryDraftUseCase.draft(memberId, aiScreen, inquiryId));
	}
}
