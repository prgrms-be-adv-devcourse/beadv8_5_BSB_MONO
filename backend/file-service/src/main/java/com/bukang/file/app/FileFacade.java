package com.bukang.file.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.file.domain.FileStatus;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.domain.exception.FileAccessDeniedException;
import com.bukang.file.domain.exception.InvalidFileException;
import com.bukang.file.domain.exception.StoredFileNotFoundException;
import com.bukang.file.dto.FileDto;
import com.bukang.file.dto.FileLinkRequestDto;
import com.bukang.file.dto.FileUploadDto;
import com.bukang.file.dto.FileUploadRequestDto;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.out.storage.FileStorage;
import com.bukang.file.out.storage.PresignedUpload;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FileFacade {
	private static final String UPLOAD_METHOD = "PUT";

	private final FileUploadUseCase fileUploadUseCase;
	private final FileLinkUseCase fileLinkUseCase;
	private final FileCleanupUseCase fileCleanupUseCase;
	private final StoredFileRepository storedFileRepository;
	private final FileStorage fileStorage;
	private final TransactionTemplate transactionTemplate;

	@Transactional
	public FileUploadDto createUploadUrl(FileUploadRequestDto request, int memberId) {
		// 용도에 맞는 형식·크기인지 검사하고, 업로드 경로(uploads/) 키로 PENDING 파일을 저장한다
		StoredFile file = fileUploadUseCase.create(
			request.getFileType(),
			request.getFileName(),
			request.getContentType(),
			request.getFileSize(),
			memberId
		);
		// 브라우저가 S3에 직접 올릴 presigned PUT URL과, 함께 보내야 할 헤더·만료 시각을 만든다 (S3 호출 없음)
		PresignedUpload upload = fileStorage.presignPut(file.getS3Key(), file.getContentType(), file.getFileSize());
		return new FileUploadDto(file.getId(), upload.url(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
	}

	// 확인에 실패해도 삭제 상태는 남겨야 하므로 InvalidFileException에는 롤백하지 않는다
	@Transactional(noRollbackFor = InvalidFileException.class)
	public FileDto completeUpload(int fileId, int memberId) {
		return toDto(fileUploadUseCase.complete(fileId, memberId));
	}

	@Transactional
	public List<FileDto> link(String refType, int refId, FileLinkRequestDto request) {
		return fileLinkUseCase.link(refType, refId, request.getOwnerId(), request.getFiles()).stream()
			.map(this::toDto)
			.toList();
	}

	// 정리 배치가 한 묶음씩 부른다. 지운 건수를 돌려주고, 0이 되면 배치가 끝난다
	// 행 삭제가 커밋된 뒤에 S3 객체를 지워야 한다. S3를 먼저 지우면 커밋이 실패했을 때 객체 없는 행이 되살아난다
	// NOT_SUPPORTED: 이 메서드는 트랜잭션 없이 실행한다 (클래스에 붙은 readOnly 트랜잭션을 쓰지 않는다)
	// 트랜잭션이 이 메서드 전체를 감싸면, 행 삭제가 그 트랜잭션에 합쳐져 메서드가 끝날 때(S3 삭제 뒤)에야 커밋된다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public int cleanupMore(int limit, LocalDateTime cutoff) {
		// transactionTemplate.execute(): 넘긴 람다만 새 트랜잭션으로 실행하고, execute()가 값을 돌려주기 전에 커밋한다
		// 그래서 다음 줄로 넘어온 시점에는 행 삭제가 이미 커밋되어 있다 (cleanupMore가 끝날 때 커밋하는 것이 아니다)
		List<String> deletedKeys = transactionTemplate.execute(status ->
			fileCleanupUseCase.deleteOldRows(limit, cutoff));
		fileCleanupUseCase.deleteObjects(deletedKeys);
		return deletedKeys.size();
	}

	// 대상에 연결된 파일만 돌려준다
	public List<FileDto> findByRef(String refType, int refId) {
		return storedFileRepository.findAllByRefTypeAndRefIdAndStatus(refType, refId, FileStatus.ACTIVE).stream()
			.sorted(StoredFile.DISPLAY_ORDER)
			.map(this::toDto)
			.toList();
	}

	// 대상에 연결된 파일은 누구나, 연결 전 파일은 올린 회원만 볼 수 있다
	public FileDto findById(int fileId, Integer memberId) {
		StoredFile file = storedFileRepository.findById(fileId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(() -> new StoredFileNotFoundException("존재하지 않는 파일입니다."));
		if (!file.isActive() && (memberId == null || !file.isOwnedBy(memberId))) {
			throw new FileAccessDeniedException("연결된 파일이 아닙니다(현재 상태: %s). 본인이 올린 파일만 볼 수 있습니다."
				.formatted(file.getStatus().getLabel()));
		}
		return toDto(file);
	}

	// 보기 URL(presigned GET)은 만료가 있어 DB에 저장하지 않고, 응답을 만들 때마다 새로 만든다
	// URL을 만들려면 S3Presigner(Spring 빈)가 필요한데 엔티티는 빈을 주입받을 수 없어서,
	// URL은 여기서 만들고 엔티티의 toDto(url)에 넘긴다
	// 업로드 확인 전 파일은 확정 경로에 객체가 없으므로 URL을 만들지 않는다
	private FileDto toDto(StoredFile file) {
		String url = file.hasConfirmedObject() ? fileStorage.presignGet(file.getS3Key()) : null;
		return file.toDto(url);
	}
}
