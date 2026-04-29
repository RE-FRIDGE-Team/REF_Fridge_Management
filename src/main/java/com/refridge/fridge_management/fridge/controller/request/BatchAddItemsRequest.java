package com.refridge.fridge_management.fridge.controller.request;

import com.refridge.fridge_management.fridge.application.usecase.fill.FillFridgeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 냉장고 채우기 배치 Request DTO.
 * {@code POST /fridge/items}
 *
 * @param items 추가할 아이템 목록 (1개 이상)
 *
 * @author 승훈
 * @since 2026-04-26
 */
public record BatchAddItemsRequest(
        @NotEmpty(message = "items는 1개 이상이어야 합니다")
        @Valid
        List<BatchAddItemsRequestItem> items
) {
    /**
     * 전체 아이템 목록을 Application Command 목록으로 변환.
     */
    public List<FillFridgeCommand> toCommands() {
        return items.stream()
                .map(BatchAddItemsRequestItem::toCommand)
                .toList();
    }
}