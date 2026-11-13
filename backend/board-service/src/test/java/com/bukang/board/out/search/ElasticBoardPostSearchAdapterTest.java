package com.bukang.board.out.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.bukang.board.domain.exception.BoardExternalServiceUnavailableException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

/**
 * 실제 Elasticsearch(nori)로 확인한다: 한국어 형태소 검색, 같은 글 다시 넣기, 지우기, 없는 인덱스, 멈춘 서버.
 * 도커가 없으면 건너뛴다 (램이 작은 PC에서는 CI가 돌린다). compose와 같은 Dockerfile(backend/docker/elasticsearch)로 이미지를 만든다
 */
@Testcontainers(disabledWithoutDocker = true)
class ElasticBoardPostSearchAdapterTest {
	private static final String IMAGE_NAME = "bukang-elasticsearch-test:9.4.5-nori";
	private static final Path DOCKERFILE = Path.of("../docker/elasticsearch/Dockerfile");

	@Container
	static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(DockerImageName
		.parse(new ImageFromDockerfile(IMAGE_NAME, false).withDockerfile(DOCKERFILE).get())
		.asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
		.withEnv("xpack.security.enabled", "false")
		.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

	private static ElasticsearchClient client;
	private ElasticBoardPostSearchAdapter adapter;

	@BeforeAll
	static void connect() {
		client = clientOf(URI.create("http://" + ELASTICSEARCH.getHttpHostAddress()));
	}

	private static ElasticsearchClient clientOf(URI uri) {
		return new ElasticsearchClient(new Rest5ClientTransport(
			Rest5Client.builder(uri).build(), new JacksonJsonpMapper()));
	}

	@BeforeEach
	void freshIndex() throws Exception {
		client.indices().delete(request -> request.index(ElasticBoardPostSearchAdapter.INDEX)
			.ignoreUnavailable(true));
		adapter = new ElasticBoardPostSearchAdapter(client);
	}

	private static BoardPostSearchPort.Document doc(long id, String title, String content) {
		return new BoardPostSearchPort.Document(id, title, content);
	}

	@Test
	@DisplayName("인덱스가 아직 없어도 검색은 빈 목록이다 (글이 하나도 색인되기 전)")
	void searchWithoutIndex() {
		assertThat(adapter.search("한강", 10)).isEmpty();
	}

	@Test
	@DisplayName("제목·본문에서 한국어 조사를 뗀 말로 찾고, 제목에 맞은 글이 본문에만 맞은 글보다 앞이 아니라 최신순으로 ID만 돌려준다")
	void searchesKoreanTitleAndContent() throws Exception {
		adapter.index(doc(1, "한강 러닝 코스", "여의도에서 시작해요"));
		adapter.index(doc(2, "제목", "주말에 한강에서 달리기를 했어요"));
		adapter.index(doc(3, "다른 글", "관계 없는 이야기"));
		client.indices().refresh(request -> request.index(ElasticBoardPostSearchAdapter.INDEX));

		assertThat(adapter.search("한강", 10)).containsExactly(2L, 1L);
		assertThat(adapter.search("달리기", 10)).containsExactly(2L);
		assertThat(adapter.search("없는말", 10)).isEmpty();
	}

	@Test
	@DisplayName("같은 글을 다시 넣으면 문서가 늘지 않고 내용만 바뀐다. 지우면 검색에서 빠지고, 없는 글을 지워도 오류가 없다")
	void indexTwiceAndDelete() throws Exception {
		adapter.index(doc(1, "처음 제목", "본문"));
		adapter.index(doc(1, "바뀐 제목", "본문"));
		client.indices().refresh(request -> request.index(ElasticBoardPostSearchAdapter.INDEX));

		assertThat(adapter.search("처음", 10)).isEmpty();
		assertThat(adapter.search("바뀐", 10)).containsExactly(1L);
		assertThat(client.count(request -> request.index(ElasticBoardPostSearchAdapter.INDEX)).count()).isEqualTo(1);

		adapter.delete(1);
		adapter.delete(999);
		client.indices().refresh(request -> request.index(ElasticBoardPostSearchAdapter.INDEX));
		assertThat(adapter.search("바뀐", 10)).isEmpty();
	}

	@Test
	@DisplayName("묶음으로 넣어도 검색되고, limit보다 많이 맞으면 최신 글(ID가 큰 쪽)부터 limit개만 준다")
	void bulkAndLimit() throws Exception {
		adapter.indexAll(List.of(doc(1, "한강 1", "본문"), doc(2, "한강 2", "본문"), doc(3, "한강 3", "본문")));
		client.indices().refresh(request -> request.index(ElasticBoardPostSearchAdapter.INDEX));

		assertThat(adapter.search("한강", 2)).containsExactly(3L, 2L);
	}

	@Test
	@DisplayName("Elasticsearch에 연결하지 못하면 503 예외로 알린다 (검색), 색인은 예외를 그대로 던져 다시 시도하게 한다")
	void unreachable() {
		ElasticBoardPostSearchAdapter down = new ElasticBoardPostSearchAdapter(
			clientOf(URI.create("http://localhost:1")));

		assertThatThrownBy(() -> down.search("한강", 10)).isInstanceOf(BoardExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> down.index(doc(1, "제목", "본문"))).isInstanceOf(RuntimeException.class);
	}
}
