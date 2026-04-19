package com.refridge.fridge_management.fridge.domain.vo;

import java.util.Set;

/**
 * FridgeItem 상태 머신.
 *
 * <pre>
 * ACTIVE ──consume()────> CONSUMED     (terminal)
 *   ├──dispose()────────> DISPOSED     (terminal)
 *   ├──portion(n)───────> PORTIONED_OUT (terminal, 자식 FridgeItem N개 생성)
 *   └──move(section)────> ACTIVE       (section만 변경, 상태 유지)
 * </pre>
 *
 * terminal 상태는 추가 전이 불가.
 */
public enum ItemStatus {
    ACTIVE,
    CONSUMED,
    DISPOSED,
    PORTIONED_OUT;

    private static final Set<ItemStatus> TERMINAL = Set.of(CONSUMED, DISPOSED, PORTIONED_OUT);

    public boolean isActive()   { return this == ACTIVE; }
    public boolean isTerminal() { return TERMINAL.contains(this); }
}