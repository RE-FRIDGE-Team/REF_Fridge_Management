package com.refridge.fridge_management.fridge.application.usecase.cook;

import com.refridge.fridge_management.fridge.application.query.FridgeItemResult;
import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import com.refridge.fridge_management.fridge.domain.vo.*;
import com.refridge.fridge_management.fridge.domain.vo.GroceryItemRef.FoodCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 즉석 요리 유스케이스 (UC8).
 *
 * <h2>UsageSpec 기반 사용량 처리</h2>
 * 각 재료는 {@code UsageSpec.toUsedRatio()}로 사용 비율을 계산한다.
 *
 * <h3>전량 사용 (ratio = 1.0)</h3>
 * {@code Fridge.cook()}에 재료 ID를 전달 → AR이 재료 CONSUMED 처리.
 *
 * <h3>부분 사용 (ratio < 1.0)</h3>
 * v3에서는 UI에서 소분 후 요리하는 흐름을 유도한다.
 * UseCase 레벨에서는 ratio < 1.0이어도 전량 소비로 처리하되,
 * 사용 비율만큼 가격 기여분만 계산해 요리 결과 totalPrice에 반영한다.
 * (향후: 소분 후 잔여 아이템 생성 흐름으로 고도화 가능)
 *
 * <h2>가격 계산</h2>
 * <pre>
 * usedPrice = purchasePrice × usedRatio
 * totalPrice = Σ usedPrice (모든 재료)
 * </pre>
 * 요리 결과 FridgeItem의 purchasePrice = totalPrice.
 * 소분된 요리당 재료비 = totalPrice / servings.
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * POST /fridge/cook
 *   ↓
 * [각 재료마다]
 *   UsageSpec.toUsedRatio() → 비율 계산
 *   usedPrice = purchasePrice × ratio
 *   재료 CONSUMED (전량 처리 — v3)
 *   ↓
 * Fridge.cook(ingredientItemIds, cookedRef, cookedExpiresAt, servings, targetSection)
 *   → FridgeItemCookedEvent 등록 (consumedIngredients 스냅샷 포함)
 *   ↓
 * BEFORE_COMMIT:
 *   FridgeOutboxAppender  → fridge:cooked XADD
 *   FridgeItemHistoryAppender → 각 재료 COOKED 이력 INSERT
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CookUseCase {

    private final FridgeRepository fridgeRepository;

    @Transactional
    public FridgeItemResult execute(String memberId, CookCommand command) {
        Fridge fridge = requireFridge(memberId);

        // UsageSpec으로 각 재료의 사용 비율 및 가격 기여분 계산
        List<String> ingredientItemIds = new ArrayList<>(command.ingredients().size());
        Money totalUsedPrice = Money.ZERO;

        for (CookIngredientCommand ingredientCmd : command.ingredients()) {
            FridgeItem ing = findActiveItem(fridge, ingredientCmd.fridgeItemId());

            BigDecimal usedRatio = ingredientCmd.usageSpec().toUsedRatio(
                    ing.getQuantity().getAmount(),
                    ing.getQuantity().getUnit().name()
            );

            // 사용분 가격 계산 (ratio 기반 비례)
            Money usedPrice = ing.getPurchasePrice()
                    .proportionalTo(usedRatio, BigDecimal.ONE);
            totalUsedPrice = totalUsedPrice.add(usedPrice);

            // v3: 전량 소비로 처리 (Fridge.cook이 CONSUMED 처리)
            ingredientItemIds.add(ingredientCmd.fridgeItemId());

            // ratio < 1.0 케이스 로그 — 향후 소분 후 요리 흐름으로 고도화 예정
            if (usedRatio.compareTo(BigDecimal.ONE) < 0) {
                log.info("[CookUseCase] 부분 사용 재료 (ratio={}) — v3에서는 전량 소비 처리. " +
                        "fridgeItemId={}", usedRatio, ingredientCmd.fridgeItemId());
            }
        }

        // 요리 결과 GroceryItemRef 구성
        GroceryItemRef cookedGroceryRef = buildCookedGroceryItemRef(command);

        // 요리 결과 소비기한
        ExpirationInfo cookedExpiresAt = ExpirationInfo.of(command.cookedExpiresAt());

        // Fridge.cook() — 재료 일괄 소비 + 요리 결과 아이템 생성
        // NOTE: Fridge.cook() 내부에서 totalPrice를 재계산하므로,
        //       여기서 계산한 totalUsedPrice는 현재 사용하지 않는다.
        //       향후 부분 사용 구현 시 Fridge.cook() 시그니처를 확장하거나
        //       usedPrice 목록을 별도 전달하는 방향으로 고도화.
        FridgeItem cookedItem = fridge.cook(
                ingredientItemIds,
                cookedGroceryRef,
                cookedExpiresAt,
                command.servings(),
                command.targetSection()
        );

        fridgeRepository.save(fridge);
        return FridgeItemResult.from(cookedItem);
    }

    /**
     * 요리 결과 GroceryItemRef 구성.
     * v3: cookedName 기반 임시 ref (category=COOKED).
     * cookedGroceryItemId가 있으면 해당 ID 사용 (core_server 등록 흐름 향후 연동).
     */
    private GroceryItemRef buildCookedGroceryItemRef(CookCommand command) {
        String groceryItemId = (command.cookedGroceryItemId() != null)
                ? command.cookedGroceryItemId()
                : "cooked:" + command.cookedName();

        return GroceryItemRef.builder()
                .groceryItemId(groceryItemId)
                .name(command.cookedName())
                .category(FoodCategory.COOKED)
                .defaultUnit("인분")
                .build();
    }

    private FridgeItem findActiveItem(Fridge fridge, String fridgeItemId) {
        return fridge.getSections().values().stream()
                .flatMap(s -> s.allItems().stream())
                .filter(i -> i.getFridgeItemId().equals(fridgeItemId) && i.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "ACTIVE 아이템을 찾을 수 없습니다. fridgeItemId=" + fridgeItemId));
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}