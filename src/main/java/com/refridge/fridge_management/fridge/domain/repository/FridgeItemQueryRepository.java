package com.refridge.fridge_management.fridge.domain.repository;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.vo.ItemStatus;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import com.refridge.fridge_management.fridge.infrastructure.persistence.querydsl.FridgeItemRepositoryImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * FridgeItem 복잡 쿼리 전용 QueryDSL 리포지토리 인터페이스.
 *
 * <h2>왜 별도 인터페이스인가?</h2>
 * Spring Data JPA의 Custom Repository 패턴:
 * {@code FridgeItemQueryRepository} (인터페이스) +
 * {@code FridgeItemRepositoryImpl} (QueryDSL 구현체)
 * 를 {@link FridgeItemRepository}가 함께 상속하면
 * Spring이 자동으로 구현체를 주입한다.
 *
 * <h2>책임</h2>
 * FridgeRepository는 Fridge AR 단위 CRUD만 담당.
 * 이 인터페이스는 FridgeItem 수준의 복잡한 동적 필터/페이지네이션 쿼리를 담당.
 *
 * @author 승훈
 * @since 2026-04-22
 * @see FridgeItemRepositoryImpl
 * @see FridgeItemRepository
 */
public interface FridgeItemQueryRepository {

    /**
     * 냉장고 아이템 동적 필터 조회 (페이지네이션).
     * UI의 냉장고 아이템 목록 API에 사용.
     *
     * @param fridgeId     냉장고 ID (필수)
     * @param sectionType  구역 필터 (null이면 전체)
     * @param status       상태 필터 (null이면 ACTIVE만)
     * @param keyword      식품명 키워드 검색 (null이면 전체)
     * @param pageable     페이지/정렬 정보
     */
    Page<FridgeItem> searchItems(
            String fridgeId,
            SectionType sectionType,
            ItemStatus status,
            String keyword,
            Pageable pageable
    );

    /**
     * 유통기한 임박 아이템 조회.
     * 배치 스케줄러(임박 알림)와 기한 연장 API에서 사용.
     *
     * @param fridgeId      냉장고 ID
     * @param today         오늘 날짜 (기준일)
     * @param thresholdDays 임박 기준 일수 (ex: 7 → 7일 이내)
     */
    List<FridgeItem> findNearExpiryItems(String fridgeId, LocalDate today, int thresholdDays);

    /**
     * 회원의 특정 기간 내 소비/폐기된 아이템 조회.
     * Saving BC의 통계 집계에 사용.
     *
     * @param memberId  회원 ID
     * @param from      기간 시작
     * @param to        기간 종료
     * @param statuses  조회할 상태 목록 (CONSUMED, DISPOSED 등)
     */
    List<FridgeItem> findByMemberAndPeriod(
            String memberId,
            LocalDate from,
            LocalDate to,
            List<ItemStatus> statuses
    );
}
