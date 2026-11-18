package com.bukang.ai.app;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bukang.ai.out.file.CertImageNotFoundException;
import com.bukang.ai.out.file.CertImagePort;
import com.bukang.ai.recordcert.CertField;
import com.bukang.ai.recordcert.CertReader;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.recordcert.event.RecordCertCheckRequestedEvent;
import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 기록증 서버 확인 요청 하나를 처리한다 (BUK-275, server-check-spec 흐름 2~3): 원본 받기 → 칸별 읽기 → "확인 결과" 보내기
 * 끝내 읽지 못하면 실패 토픽으로 보내지 않고 "모든 칸 읽지 못함 + 실패 이유"를 결과로 보낸다(11/18 A안). member가 심사 필요로 넘긴다
 * - 다시 시도할 실패: 원본 받기 실패(file · S3 응답 없음). 1초에서 시작해 두 배씩 기다리며 DOWNLOAD_ATTEMPTS번까지
 * - 바로 넘길 실패: 사진을 열 수 없음(IllegalArgumentException), 파일 없음(404)
 * 결과를 보내지 못하면 예외를 던진다. 리스너가 offset을 넘기지 않아 같은 요청을 다시 처리한다(member는 두 번째 결과를 무시)
 */
@Slf4j
@Service
public class RecordCertCheckUseCase {
	// 처음 시도를 포함한 원본 받기 횟수: 1초 → 2초 → 4초 기다리며 세 번 더 시도한다
	static final int DOWNLOAD_ATTEMPTS = 4;
	private static final long FIRST_DELAY_MILLIS = 1_000;
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final CertImagePort certImagePort;
	private final CertReader certReader;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonConverter jsonConverter;
	private final SlackNotifier slackNotifier;
	private final long firstDelayMillis;

	@Autowired
	public RecordCertCheckUseCase(CertImagePort certImagePort, CertReader certReader,
		KafkaTemplate<String, String> kafkaTemplate, JsonConverter jsonConverter, SlackNotifier slackNotifier) {
		this(certImagePort, certReader, kafkaTemplate, jsonConverter, slackNotifier, FIRST_DELAY_MILLIS);
	}

	// 테스트는 기다리지 않게 0을 넘긴다
	RecordCertCheckUseCase(CertImagePort certImagePort, CertReader certReader,
		KafkaTemplate<String, String> kafkaTemplate, JsonConverter jsonConverter, SlackNotifier slackNotifier,
		long firstDelayMillis) {
		this.certImagePort = certImagePort;
		this.certReader = certReader;
		this.kafkaTemplate = kafkaTemplate;
		this.jsonConverter = jsonConverter;
		this.slackNotifier = slackNotifier;
		this.firstDelayMillis = firstDelayMillis;
	}

	public void check(RecordCertCheckRequestedEvent request) {
		send(read(request));
	}

	private RecordCertCheckedEvent read(RecordCertCheckRequestedEvent request) {
		String failReason;
		try {
			byte[] image = download(request.fileId());
			Map<CertField, String> texts = certReader.read(image, boxes(request.fieldBoxes()),
				request.specVersion(), request.recModelSha256());
			Map<String, String> named = new LinkedHashMap<>();
			texts.forEach((field, text) -> named.put(field.name(), text));
			return RecordCertCheckedEvent.read(request.submissionId(), named);
		} catch (IllegalArgumentException broken) {
			failReason = "사진을 열 수 없음: " + broken.getMessage();
		} catch (CertImageNotFoundException notFound) {
			failReason = "파일 없음: " + notFound.getMessage();
		} catch (DownloadFailedException exhausted) {
			failReason = "재시도 끝: " + exhausted.getMessage();
		}
		String alert = "[ai] 기록증 서버 확인 실패 → 모든 칸 읽지 못함으로 보냄. submissionId=" + request.submissionId()
			+ ", fileId=" + request.fileId() + ", 이유=" + failReason;
		log.error(alert);
		slackNotifier.send(alert);
		return RecordCertCheckedEvent.failed(request.submissionId(), failReason);
	}

	private byte[] download(long fileId) {
		long delay = firstDelayMillis;
		RuntimeException last = null;
		for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
			try {
				return certImagePort.download(fileId);
			} catch (CertImageNotFoundException notFound) {
				throw notFound;
			} catch (RuntimeException failure) {
				last = failure;
				log.warn("기록증 원본을 받지 못했습니다. fileId={}, 시도 {}/{}", fileId, attempt, DOWNLOAD_ATTEMPTS, failure);
			}
			if (attempt < DOWNLOAD_ATTEMPTS) {
				sleep(delay);
				delay *= 2;
			}
		}
		throw new DownloadFailedException(last);
	}

	private void send(RecordCertCheckedEvent result) {
		try {
			kafkaTemplate.send(KafkaTopics.RECORD_CERT_CHECKED, String.valueOf(result.submissionId()),
				jsonConverter.toJson(result)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("확인 결과를 보내지 못함", interrupted);
		} catch (ExecutionException | TimeoutException failure) {
			throw new IllegalStateException("확인 결과를 보내지 못함: submissionId=" + result.submissionId(), failure);
		}
	}

	// common의 이벤트는 칸 이름을 키로 싣는다. CertReader는 float 좌표를 받는다
	private static Map<CertField, float[][]> boxes(Map<String, double[][]> named) {
		Map<CertField, float[][]> boxes = new EnumMap<>(CertField.class);
		if (named == null) {
			return boxes;
		}
		named.forEach((name, points) -> {
			float[][] box = new float[points.length][];
			for (int i = 0; i < points.length; i++) {
				box[i] = new float[points[i].length];
				for (int j = 0; j < points[i].length; j++) {
					box[i][j] = (float)points[i][j];
				}
			}
			boxes.put(CertField.valueOf(name), box);
		});
		return boxes;
	}

	private static void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("재시도 대기 중 멈춤", interrupted);
		}
	}

	private static final class DownloadFailedException extends RuntimeException {
		private DownloadFailedException(RuntimeException last) {
			super(last == null ? "원본 받기 실패" : last.getMessage(), last);
		}
	}
}
