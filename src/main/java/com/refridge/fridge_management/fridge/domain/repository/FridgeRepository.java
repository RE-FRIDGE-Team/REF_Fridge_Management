package com.refridge.fridge_management.fridge.domain.repository;

import com.refridge.fridge_management.fridge.domain.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Fridge Aggregate Root 리포지토리.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>AR(Fridge) 단위로만 저장/조회한다. FridgeSection·FridgeItem은 별도 리포지토리 없음.</li>
 *   <li>복잡한 동적 쿼리는 {@link FridgeItemQueryRepository}에 위임한다.</li>
 *   <li>{@link QuerydslPredicateExecutor}: 단순 Predicate 조합 쿼리에 사용.</li>
 * </ul>
 *
 * <h2>JOIN FETCH 쿼리</h2>
 * Fridge.sections는 EAGER이나, items는 LAZY + @BatchSize.
 * 아이템 목록까지 한 번에 필요한 경우(예: 냉장고 전체 조회 API)
 * {@link #findByMemberIdWithItems}로 items까지 JOIN FETCH한다.
 *
 * @author 승훈
 * @since 2026-04-22
 * @see FridgeItemQueryRepository
 */
public interface FridgeRepository
        extends JpaRepository<Fridge, String>, QuerydslPredicateExecutor<Fridge> {

    /**
     * memberId로 냉장고 조회 (sections EAGER, items 별도 로딩).
     * 일반적인 도메인 연산(fill/consume 등)에 사용.
     */
    Optional<Fridge> findByMemberId(String memberId);

    /**
     * memberId로 냉장고 + 모든 ACTIVE 아이템 조회.
     * 냉장고 전체 조회 API처럼 아이템 목록 전체가 필요한 경우에 사용.
     * DISTINCT: 1:N JOIN FETCH 시 카르테시안 곱 중복 제거.
     */
    @Query("""
            SELECT DISTINCT f FROM Fridge f
            JOIN FETCH f.sections s
            LEFT JOIN FETCH s.items i
            WHERE f.memberId = :memberId
              AND (i IS NULL OR i.status = 'ACTIVE')
            """)
    Optional<Fridge> findByMemberIdWithItems(@Param("memberId") String memberId);

    boolean existsByMemberId(String memberId);
}
