package com.bukang.member.recordcert.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.CheckField;
import com.bukang.member.recordcert.domain.FieldCheck;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertFileViewUnavailableException;
import com.bukang.member.recordcert.domain.exception.RecordCertNotOwnerException;
import com.bukang.member.recordcert.dto.SellerRecordCertDetailDto;
import com.bukang.member.recordcert.dto.SellerRecordCertSubmissionDto;
import com.bukang.member.recordcert.out.FileViewUrlPort;
import com.bukang.member.support.RecordCertSellerFixture;

/**
 * 판매자 기록증 제출 목록 · 상세 (BUK-276)
 * - 목록: 대회의 제출을 상태 · 서버 확인 결과로 거른다. 제출 0건 대회는 빈 목록, 제출이 있는 남의 대회는 403 (11/18)
 * - 상세: 소유를 확인한 뒤 file에서 원본 사진 보기 주소를 받아 함께 준다. file이 응답하지 않으면 503 (11/18)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({InMemoryIdentityVerificationStore.Config.class, RecordCertSellerFixture.class})
class RecordCertSellerQueryTest {
	private static final String VIEW_URL = "https://s3.example/test/files/77.jpg?X-Amz-Expires=1800";

	@Autowired
	private RecordCertSellerFacade recordCertSellerFacade;
	@Autowired
	private RecordCertSellerFixture fixture;
	@MockitoBean
	private FileViewUrlPort fileViewUrlPort;

	private long sellerId;

	@BeforeEach
	void setUp() {
		sellerId = fixture.approvedSeller();
	}

	@AfterEach
	void tearDown() {
		fixture.clear();
	}

	@Test
	@DisplayName("내 대회의 제출을 최신순으로 보고, 상태와 서버 확인 결과로 거른다")
	void listsAndFilters() {
		long matched = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, FieldCheck.MATCH);
		long mismatched = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, FieldCheck.MISMATCH);
		long approved = fixture.submission(sellerId, 1, SubmissionStatus.AUTO_APPROVED, FieldCheck.MATCH);
		fixture.submission(sellerId, 2, SubmissionStatus.NEEDS_REVIEW, FieldCheck.MATCH);

		assertThat(ids(recordCertSellerFacade.findRaceSubmissions(sellerId, 1, null, null)))
			.containsExactly(approved, mismatched, matched);
		assertThat(ids(recordCertSellerFacade.findRaceSubmissions(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, null)))
			.containsExactly(mismatched, matched);
		assertThat(ids(recordCertSellerFacade.findRaceSubmissions(sellerId, 1, null, false)))
			.containsExactly(mismatched);
		assertThat(ids(recordCertSellerFacade.findRaceSubmissions(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, true)))
			.containsExactly(matched);
	}

	@Test
	@DisplayName("목록 한 줄에 확정 값 · 판정 사유 · 칸별 서버 확인 결과가 있다")
	void listRowHasValues() {
		fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, FieldCheck.MISMATCH);

		SellerRecordCertSubmissionDto row = recordCertSellerFacade.findRaceSubmissions(sellerId, 1, null, null).get(0);

		assertThat(row.netTimeSeconds()).isEqualTo(5405);
		assertThat(row.reasons()).extracting(Enum::name).containsExactly("TOO_FAST");
		assertThat(row.serverCheck()).containsEntry(CheckField.NET_TIME, FieldCheck.MISMATCH);
		assertThat(row.serverCheckMatched()).isFalse();
	}

	@Test
	@DisplayName("제출이 한 건도 없는 대회는 빈 목록이다")
	void raceWithoutSubmissionsIsEmpty() {
		assertThat(recordCertSellerFacade.findRaceSubmissions(sellerId, 99, null, null)).isEmpty();
	}

	@Test
	@DisplayName("제출이 있는 남의 대회 목록은 볼 수 없다 (403)")
	void othersRaceIsForbidden() {
		fixture.submission(fixture.approvedSeller(), 3, SubmissionStatus.NEEDS_REVIEW);

		assertThatThrownBy(() -> recordCertSellerFacade.findRaceSubmissions(sellerId, 3, null, null))
			.isInstanceOf(RecordCertNotOwnerException.class);
	}

	@Test
	@DisplayName("상세는 읽을 부분 · 확정 값과 원본 사진 보기 주소를 함께 준다")
	void detailHasViewUrl() {
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW, FieldCheck.UNREADABLE);
		when(fileViewUrlPort.viewUrl(77)).thenReturn(VIEW_URL);

		SellerRecordCertDetailDto detail = recordCertSellerFacade.findDetail(sellerId, submissionId);

		assertThat(detail.fileUrl()).isEqualTo(VIEW_URL);
		assertThat(detail.roi()).isEqualTo(new SellerRecordCertDetailDto.Roi(10, 20, 300, 400));
		assertThat(detail.bib()).isEqualTo("M-8363");
		assertThat(detail.submission().serverCheck()).containsEntry(CheckField.COURSE, FieldCheck.UNREADABLE);
	}

	@Test
	@DisplayName("사진이 지워진 제출(file_deleted_at)의 상세는 503 대신 보기 주소 없이 준다")
	void detailWithDeletedFileHasNoViewUrl() {
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.AUTO_APPROVED, FieldCheck.MATCH);
		fixture.markFileDeleted(submissionId);

		SellerRecordCertDetailDto detail = recordCertSellerFacade.findDetail(sellerId, submissionId);

		assertThat(detail.fileUrl()).isNull();
		assertThat(detail.bib()).isEqualTo("M-8363");
		verify(fileViewUrlPort, never()).viewUrl(anyLong());
	}

	@Test
	@DisplayName("남의 제출 상세는 볼 수 없고, 보기 주소도 받지 않는다 (403)")
	void othersDetailIsForbidden() {
		long submissionId = fixture.submission(fixture.approvedSeller(), 1, SubmissionStatus.NEEDS_REVIEW);

		assertThatThrownBy(() -> recordCertSellerFacade.findDetail(sellerId, submissionId))
			.isInstanceOf(RecordCertNotOwnerException.class);
		verify(fileViewUrlPort, never()).viewUrl(anyLong());
	}

	@Test
	@DisplayName("file이 보기 주소를 주지 못하면 상세는 503이다")
	void detailFailsWhenFileServiceDown() {
		long submissionId = fixture.submission(sellerId, 1, SubmissionStatus.NEEDS_REVIEW);
		when(fileViewUrlPort.viewUrl(77)).thenThrow(new RecordCertFileViewUnavailableException());

		assertThatThrownBy(() -> recordCertSellerFacade.findDetail(sellerId, submissionId))
			.isInstanceOf(RecordCertFileViewUnavailableException.class);
	}

	private static List<Long> ids(List<SellerRecordCertSubmissionDto> rows) {
		return rows.stream().map(SellerRecordCertSubmissionDto::submissionId).toList();
	}
}
