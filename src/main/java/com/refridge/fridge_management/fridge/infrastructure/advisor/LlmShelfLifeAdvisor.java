package com.refridge.fridge_management.fridge.infrastructure.advisor;

import com.refridge.fridge_management.fridge.domain.history.FridgeItemContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 기반 소비기한 연장 추천 어드바이저.
 *
 * <h2>현재 상태 — 스텁 (Step 5 구현 전 임시)</h2>
 * Spring AI를 활용한 실제 LLM 호출은 Step 5에서 구현한다.
 * 현재는 카테고리별 기본값을 반환해 빈 등록만 처리한다.
 *
 * <h2>Step 5 구현 내용</h2>
 * {@code FridgeItemContext}(아이템 정보 + 이력)를 LLM 프롬프트로 구성해
 * OpenAI API를 호출하고 추천 연장 일수를 반환한다.
 * - 냉동 보관 + 구역 이동/소분/요리 이력 없음 → 미개봉 추정 → 더 긴 연장 추천
 * - 개봉 추정 아이템 → 짧은 연장 추천
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
public class LlmShelfLifeAdvisor implements ShelfLifeAdvisorPort {

    @Override
    public ShelfLifeRecommendation recommend(FridgeItemContext context) {
        // TODO: Step 5에서 Spring AI ChatClient를 주입받아 실제 LLM 호출로 교체
        log.info("[LlmShelfLifeAdvisor] 스텁 응답 반환. fridgeItemId={}, category={}",
                context.fridgeItemId(),
                context.groceryItemRef().getCategory());

        int days = recommendDaysByCategory(context);

        return new ShelfLifeRecommendation(
                days,
                0.5,
                "Step 5 구현 전 임시 추천값입니다. 실제 LLM 추천으로 교체 예정."
        );
    }

    /**
     * 카테고리 기반 기본 추천 일수 (스텁용).
     * Step 5에서 LLM 프롬프트로 대체된다.
     */
    private int recommendDaysByCategory(FridgeItemContext context) {
        if (context.groceryItemRef().getCategory() == null) return 3;

        return switch (context.groceryItemRef().getCategory()) {
            case GRAIN      -> context.estimatedOpened() ? 30 : 180;
            case DAIRY      -> context.estimatedOpened() ? 2  : 5;
            case MEAT       -> context.estimatedOpened() ? 1  : 3;
            case SEAFOOD    -> context.estimatedOpened() ? 1  : 2;
            case PROCESSED  -> context.estimatedOpened() ? 7  : 30;
            case VEGETABLE,
                 FRUIT      -> context.estimatedOpened() ? 2  : 5;
            default         -> 3;
        };
    }
}