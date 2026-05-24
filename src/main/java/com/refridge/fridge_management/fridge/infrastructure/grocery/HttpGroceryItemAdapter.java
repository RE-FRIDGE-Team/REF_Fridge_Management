package com.refridge.fridge_management.fridge.infrastructure.grocery;

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
 * <h2>v4 변경점</h2>
 * <ul>
 *   <li>호출 메서드 변경: {@code findById} → {@code findMetadataById}</li>
 *   <li>응답 매핑 확장: {@code allowedUsageUnits}, {@code defaultUsageUnit},
 *       {@code pieceWeightGram} 3개 필드를 {@link GroceryItemSnapshot}으로 전달</li>
 * </ul>
 *
 * <h2>핵심 변환 책임</h2>
 * <ul>
 *   <li>{@code id/productId}: core_server {@code Long} → fridge_server {@code String}</li>
 *   <li>{@code category}: core_server {@code item_type} → fridge_server {@code FoodCategory}</li>
 *   <li>단위 메타데이터: response 필드 → snapshot 필드로 그대로 패스 (해석은 도메인 책임)</li>
 * </ul>
 *
 * <h2>장애 격리</h2>
 * core_server 호출 실패 시 {@code Optional.empty()} 반환.
 * {@code FillFridgeUseCase}는 빈 값을 받으면 클라이언트가 전송한
 * 기본 정보(groceryItemId, name)만으로 GroceryItemRef를 구성한다.
 *
 * @author 승훈
 * @since 2026-05-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpGroceryItemAdapter implements GroceryItemCatalogPort {

    private final CoreServerGroceryItemClient client;

    @Override
    public Optional<GroceryItemSnapshot> fetch(String groceryItemId) {
        try {
            CoreServerGroceryItemResponse resp = client.findMetadataById(groceryItemId);

            FoodCategory category = mapCategory(resp.category(), resp.name());

            GroceryItemSnapshot snapshot = new GroceryItemSnapshot(
                    String.valueOf(resp.id()),
                    resp.name(),
                    category,
                    resp.defaultUnit(),
                    resp.minPortionAmount(),
                    resp.maxPortionAmount(),
                    resp.allowedUsageUnits(),
                    resp.defaultUsageUnit(),
                    resp.pieceWeightGram()
            );

            log.debug("[HttpGroceryItemAdapter] 조회 성공: id={}, name={}, category={}, " +
                            "defaultUnit={}, allowedUsageUnits={}, defaultUsageUnit={}, pieceWeightGram={}",
                    groceryItemId, resp.name(), category, resp.defaultUnit(),
                    resp.allowedUsageUnits(), resp.defaultUsageUnit(), resp.pieceWeightGram());

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