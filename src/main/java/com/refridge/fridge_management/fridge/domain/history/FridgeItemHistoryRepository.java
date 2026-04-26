package com.refridge.fridge_management.fridge.domain.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * FridgeItemHistory 리포지토리.
 *
 * <h2>위치 — domain.history 패키지</h2>
 * DDD 원칙상 Repository는 도메인 패키지에 위치한다.
 * {@code FridgeItemHistory}는 Fridge AR의 하위 이력 엔티티이므로
 * {@code fridge.domain.history} 패키지에 함께 둔다.
 *
 * <h2>주요 조회</h2>
 * {@code FridgeItemHistoryQueryService}가 이 리포지토리를 사용해
 * Application 레이어에 이력 데이터를 제공한다.
 * UseCase는 리포지토리를 직접 의존하지 않는다.
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FridgeItemHistory
 */
public interface FridgeItemHistoryRepository extends JpaRepository<FridgeItemHistory, String> {

    /**
     * 특정 아이템의 전체 이력 조회 (발생 시각 오름차순).
     * LLM 어드바이저에 전달할 {@code FridgeItemContext} 구성에 사용.
     *
     * @param fridgeItemId 아이템 ID
     * @return 이력 목록 (오래된 순)
     */
    @Query("""
            SELECT h FROM FridgeItemHistory h
            WHERE h.fridgeItemId = :fridgeItemId
            ORDER BY h.occurredAt ASC
            """)
    List<FridgeItemHistory> findAllByFridgeItemId(@Param("fridgeItemId") String fridgeItemId);

    /**
     * 특정 이벤트 유형 이력만 조회.
     * 개봉 여부 추정 등 단일 유형 집계에 사용.
     */
    @Query("""
            SELECT h FROM FridgeItemHistory h
            WHERE h.fridgeItemId = :fridgeItemId
              AND h.eventType = :eventType
            ORDER BY h.occurredAt ASC
            """)
    List<FridgeItemHistory> findByFridgeItemIdAndEventType(
            @Param("fridgeItemId") String fridgeItemId,
            @Param("eventType") FridgeItemHistory.HistoryEventType eventType
    );
}