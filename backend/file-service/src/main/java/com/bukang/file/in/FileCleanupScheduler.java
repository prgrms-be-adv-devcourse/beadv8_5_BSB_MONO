package com.bukang.file.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 05:00에 파일 정리 배치(fileCleanupJob)를 시작한다 (PRO-57, BATCH-ROADMAP.md).
 * prod에서만 등록한다. 개발자 PC에서 앱을 켜 둔 채 05:00이 되면 .env가 가리키는 S3(실제 버킷일 수 있다)의 객체가 지워지기 때문이다.
 */
@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {
	// 서버 시간대와 상관없이 한국 시각으로 돌고, 같은 시간대의 시각을 runDateTime으로 넣는다
	private static final String ZONE = "Asia/Seoul";

	private final JobOperator jobOperator;
	private final Job fileCleanupJob;

	@Scheduled(cron = "0 0 5 * * *", zone = ZONE)
	public void cleanupFiles() {
		// 실행 시각을 넣는다. 날마다 값이 달라 새 실행으로 인정되고, Tasklet은 여기서 1일을 빼 정리 기준 시각으로 쓴다
		// 교안은 addString으로 했다. addLocalDateTime과의 차이점은 키-값 쌍이 문자열이냐, 키-값 쌍이 LocalDateTime이냐이다.
		// 또한 Spring Batch 버전이 높아짐에 따라 Job이  LocalDateTime을 지원하게 되었다.
		// DateTimeFormatter.ISO_LOCAL_DATE_TIME으로 String 변환을 할 필요없음
		JobParameters parameters = new JobParametersBuilder()
			.addLocalDateTime(FileCleanupBatchJobConfig.RUN_DATE_TIME, LocalDateTime.now(ZoneId.of(ZONE)))
			.toJobParameters();
		try {
			// Job은 이 스레드에서 끝까지 돈 뒤 돌아온다 (기본 실행기 SyncTaskExecutor)
			// Step 안의 실패(예: S3 요청 실패)는 예외가 아니라 FAILED 상태로 돌아온다
			JobExecution execution = jobOperator.start(fileCleanupJob, parameters);
			if (execution.getStatus() != BatchStatus.COMPLETED) {
				log.error("파일 정리 배치가 실패했습니다: {}", execution.getExitStatus());
			}
		} catch (JobExecutionException exception) {
			// 시작 자체가 거절된 경우 (같은 파라미터로 이미 완료, 같은 Job이 실행 중 등)
			log.error("파일 정리 배치를 시작하지 못했습니다", exception);
		}
	}
}
