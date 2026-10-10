package com.bukang.file.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.domain.exception.FileAccessDeniedException;
import com.bukang.file.domain.exception.FileLinkConflictException;
import com.bukang.file.domain.exception.InvalidFileLinkException;
import com.bukang.file.domain.exception.StoredFileNotFoundException;
import com.bukang.file.dto.FileLinkItemDto;
import com.bukang.file.out.StoredFileRepository;

import lombok.RequiredArgsConstructor;

/**
 * 업로드를 마친 파일을 대상(대회 등)에 연결한다. 대상 서비스가 저장할 때 동기 호출로 부른다.
 * 요청한 목록이 그 대상의 파일 전체가 되도록 맞추므로, 같은 요청을 다시 보내도 결과가 같다.
 */
@Service
@RequiredArgsConstructor
public class FileLinkUseCase {
	// 대상 종류는 대상 엔티티의 클래스 이름(예: Race)이다. 보내는 쪽은 common의 FileRef.of(엔티티)로 만든다
	// 클래스 이름 규칙(대문자로 시작, 영문자·숫자)과 컬럼 길이(50자)만 확인한다. "race"처럼 잘못 적은 값을 막는다
	private static final Pattern REF_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Za-z0-9]{0,49}$");

	private final StoredFileRepository storedFileRepository;

	public List<StoredFile> link(String refType, int refId, int ownerId, List<FileLinkItemDto> items) {
		if (!REF_TYPE_PATTERN.matcher(refType).matches()) {
			throw new InvalidFileLinkException("대상 종류는 대문자로 시작하는 영문자·숫자 50자 이하로 작성해주세요.");
		}
		if (refId <= 0) {
			throw new InvalidFileLinkException("대상 ID는 0보다 커야 합니다.");
		}
		// { fileId_1 : { FileLinkItemDto1 }, fileId_2: { ... } .... } 형태 변환
		Map<Integer, FileLinkItemDto> itemsByFileId = indexByFileId(items);
		validateCounts(items); // 파일 종류별 개수 제한 검증

		Map<Integer, StoredFile> filesById = storedFileRepository.findAllById(itemsByFileId.keySet()).stream()
			.filter(file -> !file.isDeleted())
			.collect(Collectors.toMap(StoredFile::getId, Function.identity()));

		// 요청 순서대로 검사해 첫 번째 문제를 알려 준다
		for (FileLinkItemDto item : itemsByFileId.values()) {
			StoredFile file = filesById.get(item.getFileId());
			if (file == null) {
				throw new StoredFileNotFoundException(withItem("존재하지 않는 파일입니다.", item));
			}
			// 파일 링크가 가능한 FileLinkItemDto인지 검사
			checkLinkable(file, item, refType, refId, ownerId);
		}

		// 목록에서 빠진 기존 파일은 DELETED로 바꾼다 (DB 상태만 바꾸고 S3 객체는 남는다)
		// S3 객체를 지울 정리 배치는 아직 없다. 만들어야 한다 (ROADMAP 남은 것)
		storedFileRepository.findAllByRefTypeAndRefIdAndStatus(refType, refId, FileStatus.ACTIVE).stream()
			.filter(file -> !itemsByFileId.containsKey(file.getId()))
			.forEach(StoredFile::delete);
		// link 로 보내진 파일 순서대로 sortNo 와 FileStatus 상태를 변경(ACTIVE)
		itemsByFileId.forEach((fileId, item) ->
			filesById.get(fileId).linkTo(refType, refId, item.getSortNo()));

		return filesById.values().stream()
			.sorted(StoredFile.DISPLAY_ORDER)
			.toList();
	}

	private Map<Integer, FileLinkItemDto> indexByFileId(List<FileLinkItemDto> items) {
		Map<Integer, FileLinkItemDto> itemsByFileId = new LinkedHashMap<>();
		for (FileLinkItemDto item : items) {
			if (itemsByFileId.putIfAbsent(item.getFileId(), item) != null) {
				throw new InvalidFileLinkException(withItem("같은 파일을 두 번 연결할 수 없습니다.", item));
			}
		}
		return itemsByFileId;
	}

	// 용도마다 대상 하나에 연결할 수 있는 개수가 정해져 있다 (예: 대표 이미지 1장)
	private void validateCounts(List<FileLinkItemDto> items) {
		Map<FileType, Long> countsByType = items.stream()
			.collect(Collectors.groupingBy(FileLinkItemDto::getFileType, Collectors.counting()));
		countsByType.forEach((fileType, count) -> {
			if (count > fileType.getMaxCount()) {
				throw new InvalidFileLinkException(
					"%s 최대 개수(%d개)를 넘었습니다.".formatted(fileType.getLabel(), fileType.getMaxCount()));
			}
		});
	}

	private void checkLinkable(StoredFile file, FileLinkItemDto item, String refType, int refId, int ownerId) {
		if (!file.isOwnedBy(ownerId)) {
			throw new FileAccessDeniedException(withItem("본인이 올린 파일만 처리할 수 있습니다.", item));
		}
		if (file.getFileType() != item.getFileType()) {
			throw new InvalidFileLinkException(withItem(
				"업로드할 때 정한 파일 용도(%s)와 다릅니다.".formatted(file.getFileType()), item));
		}
		if (file.isLinkedTo(refType, refId)) {
			return;
		}
		if (file.isActive()) {
			throw new FileLinkConflictException(withItem("다른 대상에 연결된 파일입니다.", item));
		}
		if (!file.isUploaded()) {
			throw new FileLinkConflictException(withItem("업로드가 확인되지 않은 파일입니다.", item));
		}
	}

	// 여러 파일 중 어느 항목이 문제인지 알 수 있게 요청 항목을 메시지에 붙인다
	private String withItem(String message, FileLinkItemDto item) {
		return "%s (fileId: %d, fileType: %s, sortNo: %d)"
			.formatted(message, item.getFileId(), item.getFileType(), item.getSortNo());
	}
}
