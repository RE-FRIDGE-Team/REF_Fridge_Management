package com.refridge.fridge_management.fridge.domain.vo;

import java.util.Set;

/**
 * FridgeItem 상태 열거형.
 *
 * <h2>상태 전이</h2>
 * <pre>
 * ACTIVE ─────────────────────────────────────────────────
 *   ├─ consume()    ──► CONSUMED      (terminal: 먹기 완료)
 *   ├─ dispose()    ──► DISPOSED      (terminal: 폐기 처리)
 *   ├─ markPortionedOut() ──► PORTIONED_OUT (terminal: 소분 원본)
 *   └─ moveTo()     ──► ACTIVE        (구역 이동만, 상태 유지)
 * </pre>
 * terminal 상태({@link #isTerminal()})에서는 추가 전이가 불가능하다.
 * {@link com.refridge.fridge_management.fridge.domain.FridgeItem}의 {@code assertActive()} 메서드가 이를 강제한다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see com.refridge.fridge_management.fridge.domain.FridgeItem
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