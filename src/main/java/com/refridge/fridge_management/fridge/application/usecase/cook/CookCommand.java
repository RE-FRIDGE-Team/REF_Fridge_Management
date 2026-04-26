package com.refridge.fridge_management.fridge.application.usecase.cook;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;

import java.time.LocalDate;
import java.util.List;

/**
 * 즉석 요리 커맨드.
 *
 * <h2>재료 사용량</h2>
 * 각 재료의 사용량은 {@link CookIngredientCommand#usageSpec()}으로 명세한다.
 * 전량 사용이 아닌 경우, 사용량만큼 비례 차감되고 나머지는 FridgeItem에 잔존한다.
 * (v3: 잔존량 처리는 소분 후 요리 흐름을 UI에서 유도 — UseCase에서 전량 소비만 처리)
 *
 * <h2>cookedGroceryItemId</h2>
 * core_server에 등록된 GroceryItem이 있으면 사용, 없으면 null.
 * null인 경우 {@code CookUseCase}가 {@code cookedName}으로 임시 ref를 구성한다.
 *
 * @param ingredients         사용할 재료 목록
 * @param cookedGroceryItemId 요리 결과 GroceryItem ID (nullable)
 * @param cookedName          요리 이름 (사용자 입력)
 * @param servings            인분 수 (1 이상)
 * @param cookedExpiresAt     요리 결과 소비기한
 * @param targetSection       보관 구역
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record CookCommand(
        List<CookIngredientCommand> ingredients,
        String cookedGroceryItemId,
        String cookedName,
        int servings,
        LocalDate cookedExpiresAt,
        SectionType targetSection
) {
    public CookCommand {
        if (ingredients == null || ingredients.isEmpty())
            throw new IllegalArgumentException("요리 재료를 1개 이상 선택해야 합니다.");
        if (servings < 1)
            throw new IllegalArgumentException("인분 수는 1 이상이어야 합니다: " + servings);
        if (cookedName == null || cookedName.isBlank())
            throw new IllegalArgumentException("요리 이름을 입력해야 합니다.");
        if (cookedExpiresAt == null)
            throw new IllegalArgumentException("요리 결과 소비기한을 입력해야 합니다.");
    }
}