package com.bukang.file.in;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.file.app.FileFacade;
import com.bukang.file.dto.FileDto;
import com.bukang.file.dto.FileLinkRequestDto;

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

/**
 * 서비스끼리 부르는 내부 API. 게이트웨이에 라우팅하지 않는다(/internal/** 경로)
 * 노출되면 위험한 URL이므로 ApiV1FileController 와 구분된다.
 */
@Tag(name = "FileInternal", description = "대상(대회 등)에 파일 연결 API (서비스 간 내부 호출 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/file/refs")
public class InternalV1FileController {
	private final FileFacade fileFacade;

	@PutMapping("/{refType}/{refId}/files")
	@Operation(summary = "대상에 파일 연결",
		description = "업로드를 마친 파일을 대상에 연결한다. 요청한 목록이 그 대상의 파일 전체가 되므로 같은 요청을 다시 보내도 결과가 같고, "
			+ "목록에서 빠진 기존 파일은 삭제 상태가 된다. 파일 용도(fileType)는 업로드할 때 정한 값과 같아야 하고, "
			+ "용도마다 연결할 수 있는 개수가 정해져 있다(예: 대표 이미지 1장). 대상 서비스가 저장할 때 동기 호출로 부른다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "연결 성공",
			content = @Content(examples = @ExampleObject(value = FileInternalApiExamples.LINK_SUCCESS))),
		@ApiResponse(responseCode = "400",
			description = "입력값 검증 실패, 요청 본문 형식 오류, 대상 종류·ID 오류, 같은 파일 중복, 용도별 최대 개수 초과, "
				+ "업로드 때와 다른 용도 또는 대상 ID 형식 오류",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "입력값 검증 실패", value = FileInternalApiExamples.LINK_INVALID_INPUT),
				@ExampleObject(name = "요청 본문 형식 오류", value = FileInternalApiExamples.INVALID_BODY),
				@ExampleObject(name = "대상 종류 오류", value = FileInternalApiExamples.INVALID_REF_TYPE),
				@ExampleObject(name = "대상 ID 오류", value = FileInternalApiExamples.INVALID_REF_ID),
				@ExampleObject(name = "같은 파일 중복", value = FileInternalApiExamples.DUPLICATE_FILE),
				@ExampleObject(name = "용도별 최대 개수 초과", value = FileInternalApiExamples.TOO_MANY_FILES),
				@ExampleObject(name = "업로드 때와 다른 용도", value = FileInternalApiExamples.FILE_TYPE_MISMATCH),
				@ExampleObject(name = "대상 ID 형식 오류", value = FileInternalApiExamples.INVALID_VALUE_TYPE)
			})),
		@ApiResponse(responseCode = "403", description = "소유자가 올리지 않은 파일",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileInternalApiExamples.NOT_OWNER))),
		@ApiResponse(responseCode = "404", description = "없거나 삭제된 파일",
			content = @Content(schema = @Schema(implementation = RsData.class),
				examples = @ExampleObject(value = FileInternalApiExamples.FILE_NOT_FOUND))),
		@ApiResponse(responseCode = "409", description = "업로드 확인 전 파일 또는 다른 대상에 연결된 파일",
			content = @Content(schema = @Schema(implementation = RsData.class), examples = {
				@ExampleObject(name = "업로드 확인 전", value = FileInternalApiExamples.NOT_UPLOADED),
				@ExampleObject(name = "다른 대상에 연결됨", value = FileInternalApiExamples.LINKED_ELSEWHERE)
			}))
	})
	public ResponseEntity<RsData<List<FileDto>>> link(
		@Parameter(description = "대상 종류 (대상 엔티티 클래스 이름)", example = "Race")
		@PathVariable String refType,
		@Parameter(description = "대상 ID", example = "1")
		@PathVariable int refId,
		@Valid @RequestBody FileLinkRequestDto request
	) {
		return ResponseEntity.ok()
			.body(RsData.of(HttpStatus.OK, "파일을 연결했습니다.", fileFacade.link(refType, refId, request)));
	}
}
