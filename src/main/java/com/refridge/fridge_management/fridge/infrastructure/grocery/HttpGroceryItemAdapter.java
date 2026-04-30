package com.refridge.fridge_management.fridge.infrastructure.grocery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * core_server GroceryItem 조회 어댑터.
 *
 * <h2>현재 상태 — 스텁 (Step 5 구현 전 임시)</h2>
 * Spring Boot 4의 {@code @HttpExchange} 기반 실제 HTTP 호출은 Step 5에서 구현한다.
 * 현재는 {@code Optional.empty()}를 반환해 {@code FillFridgeUseCase}의
 * fallback 경로(클라이언트 전달 기본 정보만으로 GroceryItemRef 구성)가 동작하도록 한다.
 *
 * <h2>Step 5 구현 내용</h2>
 * core_server의 {@code GET /grocery-items/{id}} API를 호출해
 * {@code GroceryItemSnapshot}(name, category, defaultUnit, portionPolicy)을 반환한다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
public class HttpGroceryItemAdapter implements GroceryItemCatalogPort {

    @Override
    public Optional<GroceryItemSnapshot> fetch(String groceryItemId) {
        // TODO: Step 5에서 @HttpExchange CoreServerGroceryItemClient 호출로 교체
        log.debug("[HttpGroceryItemAdapter] 스텁 — core_server 미연결. groceryItemId={}",
                groceryItemId);
        return Optional.empty();
    }
}