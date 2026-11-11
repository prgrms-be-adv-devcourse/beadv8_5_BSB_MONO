package com.bukang.ai.in;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.ai.app.AiChatUseCase;
import com.bukang.ai.domain.AiScreen;
import com.bukang.ai.dto.AiChatDto;
import com.bukang.ai.dto.AiChatRequestDto;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Ai", description = "판매자·관리자 센터의 크루런 AI 질문, 새 대화 API")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ApiV1AiChatController {
	// 화면 종류는 주소로 받는다. seller·admin이 아니면 맞는 주소가 없어 404다
	private static final String CHAT_PATH = "/{screen:seller|admin}/chat";

	private final AiChatUseCase aiChatUseCase;

	@PostMapping(CHAT_PATH)
	@Operation(summary = "크루런 AI에 질문",
		description = "그 화면의 역할(승인된 판매자·관리자)을 확인한 뒤, 이 화면의 내 앞 대화와 함께 질문을 모델에 보내 답을 돌려준다. "
			+ "질문과 답은 대화 기록에 남는다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "답변 성공",
			content = @Content(examples = @ExampleObject(value = AiApiExamples.CHAT_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "질문이 비었거나 1,000자 초과, 회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 오류", value = AiApiExamples.INVALID_BODY),
				@ExampleObject(name = "회원 ID 오류", value = AiApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "그 화면의 역할이 없는 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.FORBIDDEN))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 역할을 확인할 수 없음, 또는 모델에서 답을 받지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스", value = AiApiExamples.MEMBER_UNAVAILABLE),
				@ExampleObject(name = "모델", value = AiApiExamples.MODEL_UNAVAILABLE)
			}))
	})
	public ResponseEntity<RsData<AiChatDto>> chat(
		@LoginMemberId long memberId,
		@Parameter(description = "화면 종류 (seller: 판매자 센터, admin: 관리자 센터)", example = "seller",
			schema = @Schema(allowableValues = {"seller", "admin"}))
		@PathVariable String screen,
		@Valid @RequestBody AiChatRequestDto request
	) {
		String answer = aiChatUseCase.chat(memberId, toScreen(screen), request.message());
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "답변했습니다.", new AiChatDto(answer)));
	}

	@DeleteMapping(CHAT_PATH)
	@Operation(summary = "새 대화", description = "그 화면의 역할을 확인한 뒤, 이 화면의 내 대화 기록을 지운다. 기록이 없어도 성공한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "대화 기록 지움",
			content = @Content(examples = @ExampleObject(value = AiApiExamples.CLEAR_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "회원 ID가 0 이하이거나 숫자가 아님",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.INVALID_MEMBER_ID))),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "그 화면의 역할이 없는 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = AiApiExamples.FORBIDDEN))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에 연결하지 못해 역할을 확인할 수 없음, 또는 대화 기록(Redis)을 지우지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "회원 서비스", value = AiApiExamples.MEMBER_UNAVAILABLE),
				@ExampleObject(name = "대화 기록", value = AiApiExamples.MODEL_UNAVAILABLE)
			}))
	})
	public ResponseEntity<RsData<Void>> clear(
		@LoginMemberId long memberId,
		@Parameter(description = "화면 종류 (seller: 판매자 센터, admin: 관리자 센터)", example = "seller",
			schema = @Schema(allowableValues = {"seller", "admin"}))
		@PathVariable String screen
	) {
		aiChatUseCase.clear(memberId, toScreen(screen));
		return ResponseEntity.ok(RsData.of(HttpStatus.OK, "새 대화를 시작합니다.", null));
	}

	private static AiScreen toScreen(String screen) {
		return AiScreen.valueOf(screen.toUpperCase(Locale.ROOT));
	}
}
