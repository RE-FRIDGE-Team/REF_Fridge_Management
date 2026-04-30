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
 * FridgeItem은 Fridge AR의 하위 Entity이므로
 * 원칙상 별도 리포지토리를 두지 않는 것이 맞다.
 * 그러나 다음 이유로 실용적 타협:
 * <ul>
 *   <li>임박 알림 배치: fridgeId 없이 expires_at 기반 전체 스캔이 필요</li>
 *   <li>Saving BC 이벤트 핸들러: memberId 기반 집계 쿼리가 필요</li>
 * </ul>
 * 단, 상태 변경(consume/dispose 등)은 반드시 {@link FridgeRepository}를 통해
 * Fridge AR로 접근해야 한다. 이 리포지토리는 조회 전용으로만 사용.
 *
 * <h2>v4 변경점</h2>
 * {@code findAllNearExpiryForBatch}에 {@code expires_at IS NOT NULL} 조건 추가.
 * v4부터 채우기 시점에 소비기한을 입력받지 않아 {@code expires_at}이 nullable이 됐으므로,
 * null 아이템이 배치 조회에서 자연 제외되도록 명시적 조건을 추가한다.
 *
 * @author 승훈
 * @since 2026-04-22
 * @see FridgeItemQueryRepository
 */
public interface FridgeItemRepository
        extends JpaRepository<FridgeItem, String>, FridgeItemQueryRepository {

    Optional<FridgeItem> findByFridgeItemIdAndStatus(String fridgeItemId, String status);

    /**
     * 배치 스케줄러용 소비기한 임박 아이템 전체 조회.
     *
     * <h3>v4 변경점</h3>
     * {@code expirationInfo.expiresAt IS NOT NULL} 조건 추가.
     * 소비기한 미설정 아이템({@code ExpirationInfo.unset()})은 임박 대상에서 제외된다.
     *
     * <h3>인덱스 활용</h3>
     * {@code idx_fi_expires_at (expires_at)} 인덱스 활용.
     * IS NOT NULL 조건이 추가되어도 범위 스캔 효율은 유지됨
     * (null 값은 B-tree 인덱스에서 별도 처리).
     *
     * @param today     오늘 날짜
     * @param threshold 임박 기준일 (today + thresholdDays)
     * @return 소비기한 임박 아이템 목록 (소비기한 오름차순)
     */
    @Query("""
            SELECT fi FROM FridgeItem fi
            WHERE fi.status = 'ACTIVE'
              AND fi.expirationInfo.expiresAt IS NOT NULL
              AND fi.expirationInfo.expiresAt BETWEEN :today AND :threshold
            ORDER BY fi.expirationInfo.expiresAt ASC
            """)
    List<FridgeItem> findAllNearExpiryForBatch(
            @Param("today") LocalDate today,
            @Param("threshold") LocalDate threshold
    );
}