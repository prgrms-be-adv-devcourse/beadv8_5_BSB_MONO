package com.bukang.file.in;

import static com.bukang.common.shared.file.domain.FileType.THUMBNAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.file.domain.FileFormat;
import com.bukang.file.domain.StoredFile;
import com.bukang.file.out.StoredFileRepository;
import com.bukang.file.support.FakeFileStorage;
import com.bukang.file.support.FakeFileStorageConfig;

/**
 * 파일 정리 배치(fileCleanupJob)를 실제로 실행한다. 실행 기록은 H2의 BATCH_ 테이블에 남는다.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeFileStorageConfig.class)
class FileCleanupJobTest {
	private static final int OWNER_ID = 1;
	private static final String RACE = "Race";
	private static final byte[] BODY = {0x01};

	// @SpringBatchTest가 등록한다. Job 빈이 하나뿐이라 fileCleanupJob이 자동으로 들어간다
	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private Job fileCleanupJob;

	@Autowired
	private StoredFileRepository storedFileRepository;

	@Autowired
	private FakeFileStorage fakeFileStorage;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		jobRepositoryTestUtils.removeJobExecutions();
		storedFileRepository.deleteAll();
		fakeFileStorage.clear();
	}

	@Test
	@DisplayName("runDateTime을 넣어 실행하면 1일이 지난 파일을 정리하고 COMPLETED로 끝난다. 지운 건수는 WRITE_COUNT에 남는다")
	void runJob() throws Exception {
		StoredFile uploaded = saveWithObject(uploadedFile());
		StoredFile deleted = saveWithObject(deletedFile());
		StoredFile active = saveWithObject(activeFile());

		// 파일을 지금 만들고, 배치는 이틀 뒤에 돈다고 본다 (기준 시각 = 내일)
		JobExecution execution = jobOperatorTestUtils.startJob(parametersAt(LocalDateTime.now().plusDays(2)));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		StepExecution step = execution.getStepExecutions().iterator().next();
		assertThat(step.getWriteCount()).isEqualTo(2);
		assertThat(storedFileRepository.findAllById(List.of(uploaded.getId(), deleted.getId()))).isEmpty();
		assertThat(fakeFileStorage.exists(uploaded.getS3Key())).isFalse();
		assertThat(fakeFileStorage.exists(deleted.getS3Key())).isFalse();
		assertThat(storedFileRepository.findById(active.getId())).isPresent();
		assertThat(fakeFileStorage.exists(active.getS3Key())).isTrue();
	}

	@Test
	@DisplayName("같은 runDateTime으로 다시 실행하면 이미 완료된 실행이라 거절된다")
	void rejectSameParameters() throws Exception {
		JobParameters parameters = parametersAt(LocalDateTime.now().plusDays(2));
		jobOperatorTestUtils.startJob(parameters);

		// Job 이름과 식별 파라미터로 만든 JOB_KEY가 같아 같은 실행(JobInstance)으로 본다
		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(parameters))
			.isInstanceOf(JobInstanceAlreadyCompleteException.class);
	}

	@Test
	@DisplayName("스케줄러는 지금 시각을 LocalDateTime 타입 runDateTime으로 넣어 Job을 시작한다")
	void schedulerStartsJob() {
		LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

		// 스케줄러 빈은 prod에서만 만들어지므로, 컨텍스트의 JobOperator와 Job으로 직접 만든다
		new FileCleanupScheduler(jobOperator, fileCleanupJob).cleanupFiles();

		// cleanupFiles()는 결과를 돌려주지 않으므로, Job이 남긴 실행 기록을 DB에서 읽어 확인한다 (다시 실행하지 않는다)
		JobInstance instance = jobRepository.getLastJobInstance("fileCleanupJob");
		JobExecution execution = jobRepository.getLastJobExecution(instance);
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		// BATCH_JOB_EXECUTION_PARAMS에는 타입 이름과, 문자열로 바꾼 값이 저장된다
		Map<String, Object> row = jdbcTemplate.queryForMap("""
			SELECT parameter_type, parameter_value
			FROM batch_job_execution_params
			WHERE job_execution_id = ? AND parameter_name = ?""",
			execution.getId(), FileCleanupBatchJobConfig.RUN_DATE_TIME);
		assertThat(row.get("parameter_type")).isEqualTo("java.time.LocalDateTime");
		assertThat(LocalDateTime.parse((String)row.get("parameter_value"))).isAfterOrEqualTo(before);
	}

	private JobParameters parametersAt(LocalDateTime runDateTime) {
		return new JobParametersBuilder()
			.addLocalDateTime(FileCleanupBatchJobConfig.RUN_DATE_TIME, runDateTime)
			.toJobParameters();
	}

	// 행을 저장하고, 그 키에 S3 객체도 올려 둔다
	private StoredFile saveWithObject(StoredFile file) {
		StoredFile saved = storedFileRepository.save(file);
		fakeFileStorage.upload(saved.getS3Key(), BODY, "image/png");
		return saved;
	}

	// 업로드 확인까지 마친 파일
	private StoredFile uploadedFile() {
		StoredFile file = StoredFile.pending(
			"uploads/" + UUID.randomUUID() + ".png", "race.png", THUMBNAIL, FileFormat.PNG, BODY.length, OWNER_ID);
		file.confirmUpload(file.getS3Key().replace("uploads/", "files/"));
		return file;
	}

	// 대상에 연결된 파일
	private StoredFile activeFile() {
		StoredFile file = uploadedFile();
		file.linkTo(RACE, 1, 0);
		return file;
	}

	// 다른 파일로 교체되어 연결이 끊긴 파일
	private StoredFile deletedFile() {
		StoredFile file = uploadedFile();
		file.delete();
		return file;
	}
}
