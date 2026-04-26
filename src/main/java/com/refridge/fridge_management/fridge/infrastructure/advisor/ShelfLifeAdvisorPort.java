package com.refridge.fridge_management.fridge.infrastructure.advisor;

import com.refridge.fridge_management.fridge.domain.history.FridgeItemContext;

/**
 * 소비기한 연장 추천 포트 (Outbound Port).
 *
 * <h2>구현체</h2>
 * {@code LlmShelfLifeAdvisor} — LLM 기반 추천.
 * Rule 기반 구현체(RuleBasedShelfLifeAdvisor)는 사용하지 않는다.
 *
 * <h2>FridgeItemContext 사용 이유</h2>
 * LLM이 다음 맥락을 종합 판단할 수 있도록 이력 기반 컨텍스트를 전달한다:
 * <ul>
 *   <li>보관 구역 및 이동 이력 → 온도 환경 변화</li>
 *   <li>소분·요리 사용 이력 → 개봉 여부 추정</li>
 *   <li>소비기한 연장 횟수 → 사용자 관리 패턴</li>
 *   <li>냉장고 추가 날짜 → 실제 보관 기간</li>
 * </ul>
 *
 * <h2>OCP</h2>
 * {@code FridgeItemContext}에 필드가 추가되더라도 이 포트는 변경하지 않는다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
public interface ShelfLifeAdvisorPort {

    /**
     * @param context 아이템 정보 + 이력 컨텍스트
     * @return 추천 결과
     */
    ShelfLifeRecommendation recommend(FridgeItemContext context);
}