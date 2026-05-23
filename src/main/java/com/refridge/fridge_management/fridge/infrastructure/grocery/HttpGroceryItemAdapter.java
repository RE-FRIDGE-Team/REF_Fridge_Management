package com.refridge.fridge_management.fridge.infrastructure.grocery;

import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef;
import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

/**
 * core_server GroceryItem 조회 어댑터.
 *
 * <h2>핵심 변환 책임</h2>
 * <ul>
 *   <li>{@code id/productId}: core_server {@code Long} → fridge_server {@code String}</li>
 *   <li>{@code category}: core_server {@code item_type} → fridge_server {@code FoodCategory}</li>
 * </ul>
 *
 * <h2>FoodCategory 매핑 전략</h2>
 * core_server는 대분류/중분류 2단계 카테고리(item_type)를 사용하고,
 * fridge_server는 단일 {@code FoodCategory}(12개) enum을 사용한다.
 * 계란(계란 name + RAW_MEAT item_type)은 name 기반으로 {@code EGG}로 분리한다.
 *
 * <h2>현재 상태 (API 미완성)</h2>
 * core_server의 {@code GET /grocery-items/{id}}가 미완성이므로
 * 404 또는 연결 실패 시 {@code Optional.empty()} fallback.
 * {@code FillFridgeUseCase}에서 클라이언트 전달 기본 정보로 대체 처리됨.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpGroceryItemAdapter implements GroceryItemCatalogPort {

    private final CoreServerGroceryItemClient client;

    @Override
    public Optional<GroceryItemSnapshot> fetch(String groceryItemId) {
        try {
            CoreServerGroceryItemResponse resp = client.findById(groceryItemId);

            FoodCategory category = mapCategory(resp.category(), resp.name());

            GroceryItemSnapshot snapshot = new GroceryItemSnapshot(
                    String.valueOf(resp.id()),
                    resp.name(),
                    category != null ? FoodCategory.valueOf(category.name()) : null,
                    resp.defaultUnit(),
                    resp.minPortionAmount(),
                    resp.maxPortionAmount()
            );

            log.debug("[HttpGroceryItemAdapter] 조회 성공: id={}, name={}, category={}",
                    groceryItemId, resp.name(), category);

            return Optional.of(snapshot);

        } catch (HttpClientErrorException.NotFound e) {
            log.debug("[HttpGroceryItemAdapter] 404 — GroceryItem 없음: id={}", groceryItemId);
            return Optional.empty();

        } catch (ResourceAccessException e) {
            log.warn("[HttpGroceryItemAdapter] core_server 연결 실패 — fallback: id={}", groceryItemId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[HttpGroceryItemAdapter] 조회 실패 — fallback: id={}, error={}",
                    groceryItemId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * core_server item_type → fridge_server FoodCategory 변환.
     *
     * <h3>매핑표</h3>
     * <pre>
     * RAW_MEAT           → MEAT (단, name에 "계란" 포함 시 → EGG)
     * PROCESSED_MEAT     → MEAT
     * RAW_PRODUCE        → VEGETABLE
     * GRAIN              → GRAIN
     * RAW_SEAFOOD        → SEAFOOD
     * DRIED_SEAFOOD      → SEAFOOD
     * DAIRY              → DAIRY
     * RAW_FRUIT          → FRUIT
     * SAUCE_SEASONING    → SEASONING
     * BEVERAGE           → BEVERAGE
     * ALCOHOL            → BEVERAGE
     * RTC_MEAL           → PROCESSED
     * INSTANT            → PROCESSED
     * FROZEN_MEAL        → PROCESSED
     * SIMPLE_PROCESSED   → PROCESSED
     * CANNED             → PROCESSED
     * BAKERY             → PROCESSED
     * SNACK              → PROCESSED
     * null / 알 수 없음  → ETC
     * </pre>
     *
     * @param itemType core_server item_type 문자열
     * @param name     식재료명 (계란 구분용)
     */
    private FoodCategory mapCategory(String itemType, String name) {
        if (itemType == null) return FoodCategory.ETC;

        // 계란은 core_server에서 RAW_MEAT로 분류되므로 name으로 구분
        if ("RAW_MEAT".equals(itemType) && name != null && name.contains("계란")) {
            return FoodCategory.EGG;
        }

        return switch (itemType) {
            case "RAW_MEAT",
                 "PROCESSED_MEAT"  -> FoodCategory.MEAT;
            case "RAW_PRODUCE"     -> FoodCategory.VEGETABLE;
            case "GRAIN"           -> FoodCategory.GRAIN;
            case "RAW_SEAFOOD",
                 "DRIED_SEAFOOD"   -> FoodCategory.SEAFOOD;
            case "DAIRY"           -> FoodCategory.DAIRY;
            case "RAW_FRUIT"       -> FoodCategory.FRUIT;
            case "SAUCE_SEASONING" -> FoodCategory.SEASONING;
            case "BEVERAGE",
                 "ALCOHOL"         -> FoodCategory.BEVERAGE;
            case "RTC_MEAL",
                 "INSTANT",
                 "FROZEN_MEAL",
                 "SIMPLE_PROCESSED",
                 "CANNED",
                 "BAKERY",
                 "SNACK"           -> FoodCategory.PROCESSED;
            default -> {
                log.warn("[HttpGroceryItemAdapter] 알 수 없는 item_type: {} — ETC로 매핑", itemType);
                yield FoodCategory.ETC;
            }
        };
    }
}