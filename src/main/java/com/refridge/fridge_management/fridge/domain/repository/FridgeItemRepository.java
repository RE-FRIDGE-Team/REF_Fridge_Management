package com.refridge.fridge_management.fridge.domain.repository;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * FridgeItem 리포지토리.
 *
 * <h2>DDD 원칙 주의사항</h2>
 * DDD 원칙상 FridgeItem은 Fridge AR의 하위 Entity이므로
 * 별도 리포지토리를 두는 것이 원칙에 어긋날 수 있다.
 * 그러나 다음 이유로 실용적 타협:
 * <ul>
 *   <li>배치 스케줄러(임박 알림)에서 fridgeId 없이 expires_at 기반 전체 스캔이 필요</li>
 *   <li>Saving BC 이벤트 핸들러에서 memberId 기반 집계 쿼리 필요</li>
 *   <li>두 경우 모두 Fridge를 경유하면 불필요한 sections/fridge 로딩 발생</li>
 * </ul>
 * 단, 상태 변경(consume/dispose 등) 연산은 반드시 {@link FridgeRepository}를 통해
 * Fridge AR로 접근해야 한다. 이 리포지토리는 조회 전용으로만 사용한다.
 *
 * <h2>Custom QueryDSL 쿼리</h2>
 * {@link FridgeItemQueryRepository}를 상속하여
 * {@code searchItems}, {@code findNearExpiryItems}, {@code findByMemberAndPeriod}를
 * 자동으로 사용 가능.
 *
 * @author 승훈
 * @since 2026-04-22
 * @see FridgeItemQueryRepository
 * @see com.refridge.fridge_management.fridge.infrastructure.persistence.querydsl.FridgeItemQueryRepositoryImpl
 */
public interface FridgeItemRepository
        extends JpaRepository<FridgeItem, String>, FridgeItemQueryRepository {

    Optional<FridgeItem> findByFridgeItemIdAndStatus(String fridgeItemId, String status);

    /**
     * 배치 스케줄러용 전체 서버 임박 아이템 조회.
     * {@code idx_fi_expires_at (expires_at)} 인덱스 활용.
     * fridgeId 무관 — 서버 전체 스캔 (배치만 사용, API에서 사용 금지).
     */
    @Query("""
            SELECT fi FROM FridgeItem fi
            WHERE fi.status = 'ACTIVE'
              AND fi.expirationInfo.expiresAt BETWEEN :today AND :threshold
            ORDER BY fi.expirationInfo.expiresAt ASC
            """)
    List<FridgeItem> findAllNearExpiryForBatch(
            @Param("today") LocalDate today,
            @Param("threshold") LocalDate threshold
    );
}
