package com.refridge.fridge_management.fridge.domain.vo;

/**
 * 사용자가 Recognition 결과를 수정한 필드 유형.
 *
 * <h2>역할</h2>
 * Flutter 클라이언트가 인식 결과를 수정할 때,
 * 어떤 항목을 손댔는지를 {@code POST /fridge/items} 요청에 포함해 전송한다.
 * {@code FridgeFillCompletedEvent.userEditedFields}에 담겨 Outbox를 통해
 * Feedback BC(core_server)로 전달된다.
 *
 * <h2>Feedback BC 활용 방식</h2>
 * Feedback BC의 {@code REFFillEventConsumer}는 이 필드 집합을 참조해
 * 변경이 없으면 {@code approveFeedback()},
 * 변경이 있으면 {@code correctFeedback()}으로 빠르게 분기한다.
 * (Feedback BC가 직접 diff를 계산하는 비용을 절감하면서도,
 * 신뢰성 검증을 위해 한 번 더 원본 스냅샷과 비교하는 것을 권장한다.)
 *
 * <h2>클라이언트 전송 규칙</h2>
 * <ul>
 *   <li>인식 결과와 동일한 값으로 확정한 필드는 포함하지 않는다.</li>
 *   <li>아무 수정도 하지 않은 경우 빈 Set으로 전송한다.</li>
 *   <li>Set이므로 중복은 허용하지 않는다.</li>
 * </ul>
 *
 * @author 승훈
 * @since 2026-04-26
 * @see com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent.FridgeFillCompletedEvent
 */
public enum UserEditedField {

    /**
     * 제품명(원본 OCR/바코드 텍스트)을 사용자가 직접 수정.
     * 브랜드·규격 등 raw 텍스트 수준의 변경.
     */
    PRODUCT_NAME,

    /**
     * 매핑된 식재료(GroceryItem) 자체를 변경.
     * 예: "오뚜기 진라면 매운맛" → 인식 결과가 '라면'이었는데 '즉석면'으로 변경.
     */
    GROCERY_ITEM,

    /**
     * 카테고리(FoodCategory)를 변경.
     * 예: PROCESSED → COOKED
     */
    CATEGORY,

    /**
     * 브랜드명을 변경 또는 추가.
     * 인식 결과에 브랜드가 없었거나 잘못 인식된 경우.
     */
    BRAND,

    /**
     * 수량(amount) 또는 용량(volume/unit)을 변경.
     * 예: 500g → 1kg, 1개 → 3개
     */
    QUANTITY_VOLUME
}