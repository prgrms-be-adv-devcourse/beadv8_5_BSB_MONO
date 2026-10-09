package com.bukang.file.app;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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
	// 대상 종류는 대상 엔티티의 getModelTypeCode()(클래스 이름, 예: Race)다
	private static final Pattern REF_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]{0,49}$");
	// 조회 API와 같은 순서 (용도 → 노출 순서 → ID)
	private static final Comparator<StoredFile> DISPLAY_ORDER = Comparator
		.comparing(StoredFile::getImageType)
		.thenComparingInt(StoredFile::getSortNo)
		.thenComparingInt(StoredFile::getId);

	private final StoredFileRepository storedFileRepository;

	public List<StoredFile> link(String refType, int refId, int ownerId, List<FileLinkItemDto> items) {
		validateTarget(refType, refId);
		Map<Integer, FileLinkItemDto> itemsByFileId = indexByFileId(items);

		Map<Integer, StoredFile> filesById = storedFileRepository.findAllById(itemsByFileId.keySet()).stream()
			.filter(file -> !file.isDeleted())
			.collect(Collectors.toMap(StoredFile::getId, Function.identity()));

		// 요청 순서대로 검사해 첫 번째 문제를 알려 준다
		for (Integer fileId : itemsByFileId.keySet()) {
			StoredFile file = filesById.get(fileId);
			if (file == null) {
				throw new StoredFileNotFoundException("존재하지 않는 파일입니다.");
			}
			checkLinkable(file, refType, refId, ownerId);
		}

		// 목록에서 빠진 기존 파일은 뗀다 (S3 객체는 정리 배치가 지운다)
		storedFileRepository
			.findAllByRefTypeAndRefIdAndStatusOrderByImageTypeAscSortNoAscIdAsc(refType, refId, FileStatus.ACTIVE)
			.stream()
			.filter(file -> !itemsByFileId.containsKey(file.getId()))
			.forEach(StoredFile::delete);

		itemsByFileId.forEach((fileId, item) ->
			filesById.get(fileId).linkTo(refType, refId, item.getImageType(), item.getSortNo()));

		return filesById.values().stream()
			.sorted(DISPLAY_ORDER)
			.toList();
	}

	private void validateTarget(String refType, int refId) {
		if (!REF_TYPE_PATTERN.matcher(refType).matches()) {
			throw new InvalidFileLinkException("대상 종류는 영문자와 숫자로 50자 이하로 작성해주세요.");
		}
		if (refId <= 0) {
			throw new InvalidFileLinkException("대상 ID는 0보다 커야 합니다.");
		}
	}

	private Map<Integer, FileLinkItemDto> indexByFileId(List<FileLinkItemDto> items) {
		Map<Integer, FileLinkItemDto> itemsByFileId = new LinkedHashMap<>();
		for (FileLinkItemDto item : items) {
			if (itemsByFileId.putIfAbsent(item.getFileId(), item) != null) {
				throw new InvalidFileLinkException("같은 파일을 두 번 연결할 수 없습니다.");
			}
		}
		return itemsByFileId;
	}

	private void checkLinkable(StoredFile file, String refType, int refId, int ownerId) {
		if (!file.isOwnedBy(ownerId)) {
			throw new FileAccessDeniedException("본인이 올린 파일만 처리할 수 있습니다.");
		}
		if (file.isLinkedTo(refType, refId)) {
			return;
		}
		if (file.isActive()) {
			throw new FileLinkConflictException("다른 대상에 연결된 파일입니다.");
		}
		if (!file.isUploaded()) {
			throw new FileLinkConflictException("업로드가 확인되지 않은 파일입니다.");
		}
	}
}
