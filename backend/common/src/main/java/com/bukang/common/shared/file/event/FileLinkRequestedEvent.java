package com.bukang.common.shared.file.event;

import java.util.List;

import com.bukang.common.shared.file.domain.FileRef;
import com.bukang.common.shared.file.domain.FileType;

/**
 * 파일 연결 요청 이벤트. 대상(대회·종목)을 저장한 서비스가 커밋한 뒤 발행하고, file-service가 받아 파일을 연결한다.
 * 대상마다 보낸 목록이 그 대상의 파일 전체가 된다 (빈 목록이면 그 대상의 파일을 모두 뗀다).
 * file-service는 한 메시지의 대상들을 한 트랜잭션으로 연결하므로, 하나라도 실패하면 아무것도 연결하지 않는다.
 */
public record FileLinkRequestedEvent(
	// 대상을 저장한 회원 ID. 파일을 올린 회원과 같아야 연결된다
	long ownerId,
	List<Ref> refs
) {
	/**
	 * 파일을 연결할 대상 하나. refType은 대상 엔티티의 클래스 이름이다 (예: Race, RaceCourse)
	 */
	public record Ref(String refType, long refId, List<Item> files) {
		// 문자열을 직접 적지 않도록 저장한 엔티티로 만든 FileRef로만 만든다
		public static Ref of(FileRef ref, List<Item> files) {
			return new Ref(ref.getType(), ref.getId(), files);
		}
	}

	/**
	 * 연결할 파일 하나. fileType은 업로드할 때 정한 용도와 같아야 한다
	 */
	public record Item(long fileId, FileType fileType, int sortNo) {
	}
}
