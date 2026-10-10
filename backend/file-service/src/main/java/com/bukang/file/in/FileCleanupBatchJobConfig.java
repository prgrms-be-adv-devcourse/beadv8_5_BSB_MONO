package com.bukang.file.in;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.file.app.FileFacade;

import lombok.RequiredArgsConstructor;

/**
 * 파일 정리 배치(PRO-57). 스케줄러가 실행 시각(runDateTime)을 Job 파라미터로 넣어 시작한다.
 * 마지막으로 바뀐 뒤 1일이 지난 미연결·삭제 파일을 100건씩 지우고, 지운 건수가 0이 되면 끝난다 (학원 방식: Tasklet 반복).
 */
@Configuration
@RequiredArgsConstructor
public class FileCleanupBatchJobConfig {
	// Job 파라미터 이름. 스케줄러와 테스트도 이 상수로 넣는다
	public static final String RUN_DATE_TIME = "runDateTime";
	// 한 번에 지우는 파일 수 (학원 프로젝트는 10)
	private static final int CHUNK_SIZE = 100;
	// 마지막으로 바뀐 뒤 이 기간이 지나면 정리한다
	private static final Duration RETENTION = Duration.ofDays(1);

	private final FileFacade fileFacade;

	@Bean
	public Job fileCleanupJob(JobRepository jobRepository, Step fileCleanupStep) {
		return new JobBuilder("fileCleanupJob", jobRepository)
			.start(fileCleanupStep)
			.build();
	}

	// 트랜잭션 매니저를 넘기지 않는다. 넘기지 않으면 실제 트랜잭션이 없는 ResourcelessTransactionManager를 쓴다
	// 커밋 시점은 FileFacade.cleanupMore가 정한다 (행 삭제 커밋 → S3 삭제)
	@Bean
	public Step fileCleanupStep(JobRepository jobRepository) {
		return new StepBuilder("fileCleanupStep", jobRepository)
			.tasklet((contribution, chunkContext) -> {
				LocalDateTime runDateTime = contribution.getStepExecution().getJobParameters()
					.getLocalDateTime(RUN_DATE_TIME);
				int deletedCount = fileFacade.cleanupMore(CHUNK_SIZE, runDateTime.minus(RETENTION));

				// 더 지울 파일이 없으면 Step을 끝낸다
				if (deletedCount == 0) {
					return RepeatStatus.FINISHED;
				}
				// 지운 건수를 실행 기록(BATCH_STEP_EXECUTION의 WRITE_COUNT)에 더하고, 다음 묶음을 위해 다시 부른다
				contribution.incrementWriteCount(deletedCount);
				return RepeatStatus.CONTINUABLE;
			})
			.build();
	}
}
