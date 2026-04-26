package com.refridge.fridge_management.fridge.infrastructure.grocery;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * core_server GroceryItem BC 호출 포트 (Outbound Port).
 *
 * <h2>역할</h2>
 * fridge_server가 core_server의 식재료 카탈로그에 접근하는 유일한 창구.
 * 구현체는 {@code HttpGroceryItemAdapter}(@HttpExchange)이며,
 * 테스트 시 Stub으로 교체 가능하다.
 *
 * <h2>호출 지점</h2>
 * <ul>
 *   <li>{@code FillFridgeUseCase} — 아이템 추가 시 GroceryItem 스냅샷 보강</li>
 *   <li>{@code CookUseCase} — 요리 결과 GroceryItem 등록 (선택적)</li>
 * </ul>
 *
 * <h2>장애 격리</h2>
 * core_server 호출 실패 시 {@code Optional.empty()}를 반환한다.
 * {@code FillFridgeUseCase}는 빈 값을 받으면 클라이언트가 전송한
 * 기본 정보(groceryItemId, name)만으로 GroceryItemRef를 구성한다.
 * 소분 정책(min/maxPortionAmount)은 null로 저장되며,
 * 이후 소분 시 정책 검증을 생략한다.
 *
 * @author 승훈
 * @since 2026-04-26
 * @see GroceryItemSnapshot
 */
// TODO : 구현체 생성되면 @Component 제거
@Component
public interface GroceryItemCatalogPort {

    /**
     * 식재료 ID로 GroceryItem 정보 조회.
     *
     * @param groceryItemId 조회할 식재료 ID
     * @return 식재료 스냅샷. core_server 미등록 또는 호출 실패 시 {@code Optional.empty()}
     */
    Optional<GroceryItemSnapshot> fetch(String groceryItemId);
}