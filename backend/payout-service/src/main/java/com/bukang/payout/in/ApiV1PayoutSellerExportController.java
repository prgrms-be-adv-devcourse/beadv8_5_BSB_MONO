package com.bukang.payout.in;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.LoginMemberId;
import com.bukang.payout.app.PayoutExportFile;
import com.bukang.payout.app.PayoutSellerExportFacade;
import com.bukang.payout.dto.PayoutExportRequestDto;
import com.bukang.payout.dto.PayoutItemExportRequestDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 판매자 정산 화면 「엑셀로 내려받기」. 정산 내역·건별 정산을 조회 API(GET payouts·payout-items)와 같은 값으로 CSV로 준다
 * 성공하면 RsData가 아니라 CSV 파일(text/csv, UTF-8 BOM)을 그대로 준다. 오류 응답은 RsData(data: null) 형식이다
 * 내려받을 때마다 누가 언제 무엇을 몇 줄 받았는지 기록이 남으므로(부수효과) GET이 아니라 POST다
 * 회원 ID(X-Member-Id)를 판매자 ID로 바꿔 본인 정산만 담는다. 승인된 판매자가 아니면 403, 탈퇴한 회원은 404다
 */
@Tag(name = "PayoutSellerExport", description = "판매자 정산 자료 CSV 내려받기 API (정산 내역, 건별 정산). 내려받기를 기록한다")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payout/seller")
public class ApiV1PayoutSellerExportController {
	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final PayoutSellerExportFacade payoutSellerExportFacade;

	@PostMapping("/payouts/export")
	@Operation(summary = "정산 내역 내려받기 (CSV)",
		description = "정산 조회(GET payouts)와 같은 값을 담는다. 정산 월이 기간 안인 정산 내역을 최근 달부터, "
			+ "시작 달을 포함해 최대 12개월까지 페이지 없이 모두 담는다. 엑셀에서 바로 열리는 CSV(UTF-8 BOM)다. "
			+ "내려받을 때마다 누가 언제 몇 줄 받았는지 기록한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "CSV 파일",
			content = @Content(mediaType = "text/csv",
				examples = @ExampleObject(value = PayoutSellerExportApiExamples.PAYOUTS_CSV))),
		@ApiResponse(responseCode = "400", description = "기간 한도 초과, 시작이 끝보다 늦음, 기간 없음, 값 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "12개월 초과", value = PayoutSellerApiExamples.PAYOUT_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutSellerApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간 없음", value = PayoutSellerApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "값 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<byte[]> exportPayouts(
		@LoginMemberId long memberId,
		@Valid @RequestBody PayoutExportRequestDto request
	) {
		return csv(payoutSellerExportFacade.exportPayouts(memberId, request.getFromMonth(), request.getToMonth()));
	}

	@PostMapping("/payout-items/export")
	@Operation(summary = "건별 정산 내려받기 (CSV)",
		description = "건별 정산 조회(GET payout-items)와 같은 값을 담는다. 정산 생성일이 기간 안인 건별 정산을 최근 날부터, "
			+ "끝 날이 시작 날 + 1개월 전까지(최대 1개월) 페이지 없이 모두 담는다. 엑셀에서 바로 열리는 CSV(UTF-8 BOM)다. "
			+ "내려받을 때마다 누가 언제 몇 줄 받았는지 기록한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "CSV 파일",
			content = @Content(mediaType = "text/csv",
				examples = @ExampleObject(value = PayoutSellerExportApiExamples.ITEMS_CSV))),
		@ApiResponse(responseCode = "400", description = "기간 한도 초과, 시작이 끝보다 늦음, 기간 없음, 값 형식 오류 또는 회원 ID가 0 이하",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "1개월 초과", value = PayoutSellerApiExamples.DETAIL_PERIOD_TOO_LONG),
				@ExampleObject(name = "시작이 끝보다 늦음", value = PayoutSellerApiExamples.PERIOD_REVERSED),
				@ExampleObject(name = "기간 없음", value = PayoutSellerApiExamples.MISSING_PARAMETER),
				@ExampleObject(name = "값 형식 오류", value = PayoutSellerApiExamples.INVALID_VALUE_TYPE),
				@ExampleObject(name = "회원 ID가 0 이하", value = PayoutSellerApiExamples.INVALID_MEMBER_ID)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "판매자 신청이 없거나 대기 · 반려 상태",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_NOT_APPROVED))),
		@ApiResponse(responseCode = "404", description = "존재하지 않거나 탈퇴한 회원",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.MEMBER_NOT_FOUND))),
		@ApiResponse(responseCode = "503", description = "회원 서비스에서 판매자 정보를 확인하지 못함",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = PayoutSellerApiExamples.SELLER_LOOKUP_UNAVAILABLE)))
	})
	public ResponseEntity<byte[]> exportPayoutItems(
		@LoginMemberId long memberId,
		@Valid @RequestBody PayoutItemExportRequestDto request
	) {
		return csv(payoutSellerExportFacade.exportPayoutItems(memberId, request.getFrom(), request.getTo()));
	}

	private static ResponseEntity<byte[]> csv(PayoutExportFile file) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(file.fileName()).build().toString())
			.body(file.content());
	}
}
