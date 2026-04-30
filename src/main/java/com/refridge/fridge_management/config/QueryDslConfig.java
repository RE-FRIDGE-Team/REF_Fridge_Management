package com.refridge.fridge_management.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.fridge_management.fridge.infrastructure.persistence.querydsl.FridgeItemRepositoryImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정.
 *
 * <h2>JPAQueryFactory Bean 등록</h2>
 * {@code @PersistenceContext}로 주입받은 EntityManager를 QueryFactory에 전달.
 * Spring이 트랜잭션 범위에 맞게 EntityManager를 프록시로 감싸 관리하므로
 * 싱글턴 Bean으로 등록해도 스레드 안전하다.
 *
 * @author 승훈
 * @since 2026-04-22
 * @see FridgeItemRepositoryImpl
 */
@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
