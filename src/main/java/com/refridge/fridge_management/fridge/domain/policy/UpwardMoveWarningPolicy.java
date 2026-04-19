package com.refridge.fridge_management.fridge.domain.policy;

import com.refridge.fridge_management.fridge.domain.vo.SectionType;

/**
 * 상행성 이동 경고 정책.
 *
 * <p>"냉동→냉장→상온" 방향의 이동은 온도가 올라가는 방향(상행성)으로,
 * 식품 안전 위험이 있으므로 경고를 표시해야 한다.
 *
 * <p>Fridge.moveItem() 호출 시 이 정책을 통해 경고 여부를 판단하고
 * FridgeItemMovedEvent에 wasUpwardMove 플래그를 포함시킨다.
 */
public class UpwardMoveWarningPolicy {

    /**
     * 상행성 이동 여부 판단.
     *
     * @param from 현재 구역
     * @param to 목적지 구역
     * @return true이면 경고 필요 (냉동→냉장, 냉동→상온, 냉장→상온)
     */
    public boolean isUpwardMove(SectionType from, SectionType to) {
        return from.isUpwardMoveTo(to);
    }

    /**
     * 이동 가능 여부 판단 (같은 구역으로 이동 불가).
     *
     * @throws IllegalArgumentException 동일 구역으로 이동 시도 시
     */
    public void validateMove(SectionType from, SectionType to) {
        if (from == to) {
            throw new IllegalArgumentException(
                    "이미 해당 구역(%s)에 있는 아이템입니다.".formatted(from.getDisplayName())
            );
        }
    }
}