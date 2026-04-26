package com.refridge.fridge_management.fridge.application.usecase.fill;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import com.refridge.fridge_management.fridge.domain.vo.*;
import com.refridge.fridge_management.fridge.infrastructure.grocery.GroceryItemCatalogPort;
import com.refridge.fridge_management.fridge.infrastructure.grocery.GroceryItemSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 냉장고 채우기 유스케이스 (UC1).
 *
 * <h2>소비기한 미설정 정책</h2>
 * 채우기 시점에는 소비기한을 입력받지 않는다.
 * {@code Fridge.fill()}은 {@code ExpirationInfo.unset()}으로 FridgeItem을 생성하며,
 * 사용자는 이후 별도로 {@code POST /fridge/items/{id}/expiration}으로 소비기한을 등록한다.
 *
 * <h2>처리 흐름 (한 트랜잭션)</h2>
 * <pre>
 * [각 아이템마다]
 *   1. GroceryItemCatalogPort.fetch() → GroceryItemRef 스냅샷 보강
 *   2. Fridge.fill()                  → FridgeItem 생성 (소비기한 미설정)
 *   3. Fridge.registerFillCompletedEvent() → FridgeFillCompletedEvent 등록
 *   ↓
 * FridgeRepository.save()
 *   ↓
 * BEFORE_COMMIT:
 *   FridgeOutboxAppender  → fridge_pending_event INSERT
 *   FridgeItemHistoryAppender → ADDED 이력 INSERT
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FillFridgeUseCase {

    private final FridgeRepository fridgeRepository;
    private final GroceryItemCatalogPort groceryItemCatalogPort;

    @Transactional
    public List<FillFridgeResult> execute(String memberId, List<FillFridgeCommand> commands) {
        Fridge fridge = fridgeRepository.findByMemberId(memberId)
                .orElseGet(() -> fridgeRepository.save(Fridge.create(memberId)));

        List<FillFridgeResult> results = new ArrayList<>(commands.size());

        for (FillFridgeCommand cmd : commands) {
            GroceryItemRef groceryItemRef = buildGroceryItemRef(cmd);

            // 소비기한 미설정 — 채우기 시점에는 입력받지 않음
            FridgeItem item = fridge.fill(
                    groceryItemRef,
                    Quantity.of(cmd.quantityAmount(), Quantity.QuantityUnit.valueOf(cmd.quantityUnit())),
                    Money.of(cmd.purchasePrice()),
                    cmd.sectionType(),
                    cmd.processingType()
            );

            fridge.registerFillCompletedEvent(
                    cmd.recognitionId(),
                    item,
                    cmd.finalBrandName(),
                    cmd.userEditedFields()
            );

            results.add(FillFridgeResult.from(item));
        }

        fridgeRepository.save(fridge);
        return results;
    }

    private GroceryItemRef buildGroceryItemRef(FillFridgeCommand cmd) {
        GroceryItemRef.Builder builder = GroceryItemRef.builder()
                .groceryItemId(cmd.groceryItemId())
                .productId(cmd.productId());

        groceryItemCatalogPort.fetch(cmd.groceryItemId())
                .ifPresentOrElse(
                        snap -> applySnapshot(builder, snap),
                        () -> log.warn("[FillFridgeUseCase] GroceryItem 조회 실패 — fallback. groceryItemId={}",
                                cmd.groceryItemId())
                );

        return builder.build();
    }

    private void applySnapshot(GroceryItemRef.Builder builder, GroceryItemSnapshot snap) {
        builder.name(snap.name())
                .category(snap.category())
                .defaultUnit(snap.defaultUnit())
                .minPortionAmount(snap.minPortionAmount())
                .maxPortionAmount(snap.maxPortionAmount());
    }
}