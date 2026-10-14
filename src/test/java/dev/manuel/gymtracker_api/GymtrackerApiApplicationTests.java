package dev.manuel.gymtracker_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest
class GymtrackerApiApplicationTests {

    @org.testcontainers.junit.jupiter.Container
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres =
            new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17");

    @org.springframework.test.context.DynamicPropertySource
    static void configureDatasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }



	@Test
	void contextLoads() {
	}

}
