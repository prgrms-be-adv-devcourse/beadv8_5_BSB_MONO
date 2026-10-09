package com.bukang.file.in;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.file.app.FileFacade;
import com.bukang.file.dto.FileDto;
import com.bukang.file.dto.FileUploadDto;
import com.bukang.file.dto.FileUploadRequestDto;

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

@Tag(name = "File", description = "이미지 업로드 URL 발급, 업로드 완료 확인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/file/files")
public class ApiV1FileController {
	private final FileFacade fileFacade;

	// ResponseEntity.created()의 201은 springdoc이 추론하지 못하므로 응답 코드를 직접 명시한다
	// 에러 응답은 GlobalExceptionHandler, FileExceptionHandler가 RsData(data: null) 형식으로 반환한다
	@PostMapping
	@Operation(summary = "업로드 URL 발급",
		description = "형식(JPG·PNG·WebP)과 크기(10MB 이하)를 검사한 뒤 파일을 업로드 대기 상태로 만들고, "
			+ "S3에 직접 올릴 presigned PUT URL(10분)을 발급한다. 브라우저는 응답의 headers를 그대로 붙여 PUT 한다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "발급 성공",
			content = @Content(examples = @ExampleObject(value = FileApiExamples.ISSUE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "입력값 검증 실패, 요청 본문 형식 오류, 받을 수 없는 형식, 10MB 초과 또는 회원 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = FileApiExamples.ISSUE_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = FileApiExamples.INVALID_BODY),
				@ExampleObject(name = "받을 수 없는 형식", value = FileApiExamples.UNSUPPORTED_TYPE),
				@ExampleObject(name = "10MB 초과", value = FileApiExamples.TOO_LARGE),
				@ExampleObject(name = "회원 ID 형식 오류", value = FileApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileApiExamples.LOGIN_REQUIRED)))
	})
	public ResponseEntity<RsData<FileUploadDto>> issueUploadUrl(
		@Parameter(description = "요청한 회원 ID (게이트웨이가 넣어 줄 예정)", example = "1")
		@RequestHeader(MemberIdHeader.NAME) int memberId,
		@Valid @RequestBody FileUploadRequestDto request
	) {
		FileUploadDto upload = fileFacade.issueUploadUrl(request, memberId);
		return ResponseEntity.created(URI.create("/api/v1/file/files/" + upload.getFileId()))
			.body(RsData.of(HttpStatus.CREATED, "업로드 URL을 발급했습니다.", upload));
	}

	@PostMapping("/{fileId}/complete")
	@Operation(summary = "업로드 완료 확인",
		description = "S3에 올라간 파일의 크기와 앞부분(매직 바이트)이 발급 때 신고한 크기·형식과 같은지 확인해 업로드 완료로 바꾸고, "
			+ "이미지를 볼 수 있는 presigned GET URL(30분)을 돌려준다. 다르면 올라간 파일을 지운다. 이미 확인한 파일은 같은 결과를 돌려준다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "확인 성공",
			content = @Content(examples = @ExampleObject(value = FileApiExamples.COMPLETE_SUCCESS))),
		@ApiResponse(responseCode = "400", description = "업로드 안 됨, 신고한 형식·크기와 다름 또는 파일 ID·회원 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "업로드 안 됨", value = FileApiExamples.NOT_UPLOADED),
				@ExampleObject(name = "신고한 형식·크기와 다름", value = FileApiExamples.CONTENT_MISMATCH),
				@ExampleObject(name = "파일 ID·회원 ID 형식 오류", value = FileApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "401", description = "회원 ID 헤더 없음",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileApiExamples.LOGIN_REQUIRED))),
		@ApiResponse(responseCode = "403", description = "다른 회원이 올린 파일",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없거나 삭제된 파일",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileApiExamples.FILE_NOT_FOUND)))
	})
	public ResponseEntity<RsData<FileDto>> completeUpload(
		@Parameter(description = "요청한 회원 ID (게이트웨이가 넣어 줄 예정)", example = "1")
		@RequestHeader(MemberIdHeader.NAME) int memberId,
		@Parameter(description = "업로드 URL 발급 때 받은 파일 ID", example = "1")
		@PathVariable int fileId
	) {
		FileDto file = fileFacade.completeUpload(fileId, memberId);
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "업로드를 확인했습니다.", file));
	}
}
