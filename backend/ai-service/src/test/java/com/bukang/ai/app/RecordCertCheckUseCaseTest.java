package com.bukang.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.bukang.ai.out.file.CertImageNotFoundException;
import com.bukang.ai.out.file.CertImagePort;
import com.bukang.ai.recordcert.CertField;
import com.bukang.ai.recordcert.CertReader;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.recordcert.event.RecordCertCheckRequestedEvent;
import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;

import tools.jackson.databind.json.JsonMapper;

/**
 * 확인 요청 처리 (BUK-275, server-check-spec의 ai-service 심). 원본 받기 · 읽기 · Slack · Kafka를 목으로 두고
 * 요청 하나를 처리했을 때 보낸 "확인 결과"만 본다. 재시도 간격은 0으로 둔다
 */
class RecordCertCheckUseCaseTest {
	private static final long SUBMISSION_ID = 5;
	private static final long FILE_ID = 7;
	private static final byte[] IMAGE = {1, 2, 3};
	private static final RecordCertCheckRequestedEvent REQUEST = new RecordCertCheckRequestedEvent("e-1",
		SUBMISSION_ID, FILE_ID, Map.of("NET_TIME", new double[][] {{1, 2}, {3, 2}, {3, 4}, {1, 4}}), "1", "sha");

	private final JsonConverter jsonConverter = new JsonConverter(JsonMapper.builder().build());
	private CertImagePort certImagePort;
	private CertReader certReader;
	private KafkaTemplate<String, String> kafkaTemplate;
	private SlackNotifier slackNotifier;
	private RecordCertCheckUseCase useCase;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		certImagePort = mock(CertImagePort.class);
		certReader = mock(CertReader.class);
		kafkaTemplate = mock(KafkaTemplate.class);
		slackNotifier = mock(SlackNotifier.class);
		given(kafkaTemplate.send(anyString(), anyString(), anyString()))
			.willReturn(CompletableFuture.completedFuture(null));
		useCase = new RecordCertCheckUseCase(certImagePort, certReader, kafkaTemplate, jsonConverter,
			slackNotifier, 0);
	}

	@Test
	@DisplayName("읽은 칸별 글자를 확인 결과로 보낸다")
	void sendsReadTexts() {
		given(certImagePort.download(FILE_ID)).willReturn(IMAGE);
		Map<CertField, String> texts = new EnumMap<>(CertField.class);
		texts.put(CertField.NET_TIME, "01:30:05");
		given(certReader.read(eq(IMAGE), any(), eq("1"), eq("sha"))).willReturn(texts);

		useCase.check(REQUEST);

		RecordCertCheckedEvent sent = sent();
		assertThat(sent.submissionId()).isEqualTo(SUBMISSION_ID);
		assertThat(sent.texts()).containsExactly(Map.entry("NET_TIME", "01:30:05"));
		assertThat(sent.failReason()).isNull();
		verify(slackNotifier, never()).send(anyString());
	}

	@Test
	@DisplayName("상자에 null이 섞여 와도 예외 없이 그 칸을 null 상자로 넘겨 확인 결과를 보낸다 (리뷰 #645)")
	void nullBoxesAreReadAsUnreadable() {
		Map<String, double[][]> boxes = new HashMap<>();
		boxes.put("NET_TIME", null);
		boxes.put("COURSE", new double[][] {{1, 2}, null, {3, 4}, {1, 4}});
		RecordCertCheckRequestedEvent request = new RecordCertCheckRequestedEvent("e-2", SUBMISSION_ID, FILE_ID,
			boxes, "1", "sha");
		given(certImagePort.download(FILE_ID)).willReturn(IMAGE);
		given(certReader.read(eq(IMAGE), any(), eq("1"), eq("sha"))).willReturn(new EnumMap<>(CertField.class));

		useCase.check(request);

		ArgumentCaptor<Map<CertField, float[][]>> passed = ArgumentCaptor.captor();
		verify(certReader).read(eq(IMAGE), passed.capture(), eq("1"), eq("sha"));
		assertThat(passed.getValue()).containsEntry(CertField.NET_TIME, null).containsEntry(CertField.COURSE, null);
		assertThat(sent().failReason()).isNull();
	}

	@Test
	@DisplayName("사진을 열 수 없으면 재시도 없이 모든 칸 읽지 못함과 이유를 보낸다")
	void brokenImageSendsFailureWithoutRetry() {
		given(certImagePort.download(FILE_ID)).willReturn(IMAGE);
		given(certReader.read(eq(IMAGE), any(), anyString(), anyString()))
			.willThrow(new IllegalArgumentException("기록증 사진을 열 수 없음"));

		useCase.check(REQUEST);

		RecordCertCheckedEvent sent = sent();
		assertThat(sent.texts()).isEmpty();
		assertThat(sent.failReason()).contains("사진을 열 수 없음");
		verify(certImagePort, times(1)).download(FILE_ID);
		verify(slackNotifier).send(anyString());
	}

	@Test
	@DisplayName("인식 모델 실행 실패처럼 예상하지 못한 예외도 모든 칸 읽지 못함과 이유를 보낸다")
	void unexpectedReadFailureSendsFailure() {
		given(certImagePort.download(FILE_ID)).willReturn(IMAGE);
		given(certReader.read(eq(IMAGE), any(), anyString(), anyString()))
			.willThrow(new IllegalStateException("인식 모델 실행 실패"));

		useCase.check(REQUEST);

		RecordCertCheckedEvent sent = sent();
		assertThat(sent.texts()).isEmpty();
		assertThat(sent.failReason()).contains("읽기 실패").contains("인식 모델 실행 실패");
		verify(slackNotifier).send(anyString());
	}

	@Test
	@DisplayName("파일이 없으면 재시도 없이 모든 칸 읽지 못함과 이유를 보낸다")
	void missingFileSendsFailureWithoutRetry() {
		given(certImagePort.download(FILE_ID)).willThrow(new CertImageNotFoundException(FILE_ID));

		useCase.check(REQUEST);

		RecordCertCheckedEvent sent = sent();
		assertThat(sent.texts()).isEmpty();
		assertThat(sent.failReason()).contains("파일 없음");
		verify(certImagePort, times(1)).download(FILE_ID);
		verify(slackNotifier).send(anyString());
	}

	@Test
	@DisplayName("원본을 끝내 받지 못하면 재시도한 뒤 모든 칸 읽지 못함과 이유를 보낸다")
	void downloadFailureRetriesThenSendsFailure() {
		given(certImagePort.download(FILE_ID)).willThrow(new IllegalStateException("file 응답 없음"));

		useCase.check(REQUEST);

		RecordCertCheckedEvent sent = sent();
		assertThat(sent.texts()).isEmpty();
		assertThat(sent.failReason()).contains("재시도 끝");
		verify(certImagePort, times(RecordCertCheckUseCase.DOWNLOAD_ATTEMPTS)).download(FILE_ID);
		verify(certReader, never()).read(any(), any(), any(), any());
		verify(slackNotifier).send(anyString());
	}

	private RecordCertCheckedEvent sent() {
		ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq(KafkaTopics.RECORD_CERT_CHECKED), eq(String.valueOf(SUBMISSION_ID)),
			payload.capture());
		return jsonConverter.fromJson(payload.getValue(), RecordCertCheckedEvent.class);
	}
}
