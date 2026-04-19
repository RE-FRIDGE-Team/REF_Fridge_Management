package com.refridge.fridge_management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring 컨텍스트 로드 검증 테스트.
 *
 * DB / Redis 없이 순수하게 ApplicationContext가 정상 구성되는지 확인.
 * 영속성 통합 테스트는 별도 클래스(@DataJpaTest + Testcontainers)로 분리.
 *
 * exclude 이유:
 *  - HibernateJpaAutoConfiguration: DB 연결 없이 EntityManagerFactory 생성 불가
 *  - FlywayAutoConfiguration: DB 연결 필요
 *  - RedisAutoConfiguration: Redis 서버 연결 필요
 */
@SpringBootTest
@ActiveProfiles("test")
class FridgeManagementApplicationTests {

    @Test
    void contextLoads() {
        // Spring 컨텍스트가 오류 없이 로드되면 통과
    }
}