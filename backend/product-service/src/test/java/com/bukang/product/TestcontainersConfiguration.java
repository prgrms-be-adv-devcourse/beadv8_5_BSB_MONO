package com.bukang.product;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.exception.NotFoundException;

/**
 * 테스트용 Elasticsearch·Ollama 컨테이너. 테스트 클래스에 @Import로 붙인다. Docker가 없으면 테스트를 건너뛴다(ProductIntegrationTest).
 * 공식 이미지에는 nori가 없어 race-settings.json의 분석기로 인덱스를 만들 수 없으므로,
 * compose와 같은 Dockerfile(backend/docker/elasticsearch)로 이미지를 만든다. 이미지는 지우지 않아 다음 실행부터 재사용된다.
 * 접속 주소(spring.elasticsearch.uris)는 @ServiceConnection이 넣어 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
	private static final String IMAGE_NAME = "bukang-elasticsearch-test:9.4.5-nori";
	// compose의 bukang-ollama와 같은 버전
	private static final String OLLAMA_IMAGE = "ollama/ollama:0.40.15";
	// OLLAMA_IMAGE에 임베딩 모델을 받아 둔 이미지. 지우지 않아 다음 실행부터 재사용된다 (지우면 다음 실행에서 다시 만든다)
	private static final String OLLAMA_MODEL_IMAGE = "bukang-ollama-test:0.40.15-nomic-embed-text";
	// 테스트 실행 위치(product-service 모듈 폴더) 기준
	private static final Path DOCKERFILE = Path.of("../docker/elasticsearch/Dockerfile");

	@Bean
	@ServiceConnection
	ElasticsearchContainer elasticsearchContainer() {
		String image = new ImageFromDockerfile(IMAGE_NAME, false)
			.withDockerfile(DOCKERFILE)
			.get();
		return new ElasticsearchContainer(DockerImageName.parse(image)
			.asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
			// compose와 같은 로컬 개발용 설정 (보안 끔, 힙 512MB)
			.withEnv("xpack.security.enabled", "false")
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
	}

	// 테스트용 Ollama (AI 추천 임베딩, BUK-162). 접속 주소(spring.ai.ollama.base-url)는 @ServiceConnection이 넣어 준다
	// 임베딩 모델(약 270MB)을 실행마다 받지 않도록, 처음 한 번 받은 뒤 모델이 든 이미지(OLLAMA_MODEL_IMAGE)로 저장해 다시 쓴다
	// CI(GitHub Actions가 CI=true를 넣는다)에서는 띄우지 않는다. Ollama 이미지가 8GB를 넘어 매번 받으면 러너 디스크가 모자라고 느리다
	//   CI에서는 Ollama를 쓰는 테스트(RaceSearchSyncTest)를 건너뛰고, 나머지 테스트는 임베딩을 부르지 않아 Ollama 없이 돈다
	@Bean
	@ServiceConnection
	@ConditionalOnExpression("'${CI:false}' != 'true'")
	OllamaContainer ollamaContainer(@Value("${spring.ai.ollama.embedding.model}") String model) throws Exception {
		if (!imageExists(OLLAMA_MODEL_IMAGE)) {
			try (OllamaContainer base = new OllamaContainer(OLLAMA_IMAGE)) {
				base.start();
				base.execInContainer("ollama", "pull", model);
				base.commitToImage(OLLAMA_MODEL_IMAGE);
			}
		}
		return new OllamaContainer(DockerImageName.parse(OLLAMA_MODEL_IMAGE).asCompatibleSubstituteFor(OLLAMA_IMAGE));
	}

	// 로컬에 이미지가 있는지 (이미지 이름 거르기 옵션은 최신 Docker에서 무시되므로 이미지를 직접 조회한다)
	private static boolean imageExists(String imageName) {
		try {
			DockerClientFactory.lazyClient().inspectImageCmd(imageName).exec();
			return true;
		} catch (NotFoundException exception) {
			return false;
		}
	}
}
