package com.bukang.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bukang.product.support.StubPortsConfig;

// ES 저장소가 뜰 때 인덱스를 만들므로 ES가 필요하다. Docker가 없으면 이 테스트를 건너뛴다
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, StubPortsConfig.class})
class ProductApplicationTests {

	@Test
	void contextLoads() {
	}

}
