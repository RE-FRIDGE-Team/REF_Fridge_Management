package com.refridge.fridge_management.fridge.domain.history;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.ItemProcessingType;
import com.refridge.fridge_management.fridge.domain.vo.ExpirationInfo;
import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef;
import com.refridge.fridge_management.fridge.domain.vo.Money;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM 소비기한 추천 어드바이저에 전달하는 아이템 컨텍스트.
 *
 * <h2>역할</h2>
 * {@code ShelfLifeAdvisorPort.recommend(FridgeItemContext)}의 입력값.
 * FridgeItem 단일 엔티티로는 알 수 없는 "이력 기반 맥락"을 포함한다.
 * LLM이 다음과 같은 판단을 내릴 수 있도록 충분한 정보를 제공한다:
 * <ul>
 *   <li>냉동 보관이며 한 번도 구역 이동이 없었음 → 미개봉 가능성 높음</li>
 *   <li>소분 또는 요리 재료로 사용됨 → 개봉됨</li>
 *   <li>소비기한이 N회 연장됨 → 사용자가 신중하게 관리 중</li>
 * </ul>
 *
 * <h2>OCP 설계</h2>
 * {@code historyEntries}를 추가함으로써 {@code ShelfLifeAdvisorPort} 시그니처 변경 없이
 * LLM이 활용할 수 있는 정보를 확장할 수 있다.
 * 향후 영양 정보, 구매처, 평균 소비 패턴 등의 필드를 추가해도
 * 포트 인터페이스는 그대로 유지된다.
 *
 * @param fridgeItemId    아이템 ID
 * @param groceryItemRef  식재료 정보 스냅샷
 * @param addedAt         냉장고에 추가된 날짜 (ADDED 이력의 occurredAt)
 * @param currentSection  현재 보관 구역
 * @param expirationInfo  소비기한 정보 (연장 횟수 포함)
 * @param purchasePrice   구매 가격
 * @param processingType  가공 유형
 * @param estimatedOpened 개봉된 것으로 추정되는지 여부 (이력 기반 파생)
 * @param historyEntries  전체 이력 목록 (오래된 순)
 *
 * @author 승훈
 * @since 2026-04-26
 * @see com.refridge.fridge_management.fridge.infrastructure.advisor.ShelfLifeAdvisorPort
 */
public record FridgeItemContext(
        String fridgeItemId,
        GroceryItemRef groceryItemRef,
        LocalDate addedAt,
        SectionType currentSection,
        ExpirationInfo expirationInfo,
        Money purchasePrice,
        ItemProcessingType processingType,
        boolean estimatedOpened,
        List<FridgeItemHistory> historyEntries
) {
    /**
     * FridgeItem + 이력 목록으로 컨텍스트 생성.
     *
     * <h3>estimatedOpened 판단 기준</h3>
     * 이력 중 {@code MOVED}, {@code PORTIONED}, {@code COOKED} 중 하나라도 있으면
     * 아이템이 실제로 접촉됐을 가능성이 높으므로 개봉된 것으로 간주한다.
     *
     * <h3>addedAt 추출</h3>
     * {@code ADDED} 이력의 {@code occurredAt}에서 날짜를 추출한다.
     * ADDED 이력이 없으면 오늘 날짜를 fallback으로 사용한다.
     * (Appender 장애로 이력이 누락된 경우를 방어)
     */
    public static FridgeItemContext of(FridgeItem item, List<FridgeItemHistory> history) {
        boolean estimatedOpened = history.stream().anyMatch(FridgeItemHistory::impliesOpened);

        LocalDate addedAt = history.stream()
                .filter(h -> h.getEventType() == FridgeItemHistory.HistoryEventType.ADDED)
                .findFirst()
                .map(h -> h.getOccurredAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                .orElse(LocalDate.now());

        return new FridgeItemContext(
                item.getFridgeItemId(),
                item.getGroceryItemRef(),
                addedAt,
                item.getSectionType(),
                item.getExpirationInfo(),
                item.getPurchasePrice(),
                item.getProcessingType(),
                estimatedOpened,
                List.copyOf(history)
        );
    }
}