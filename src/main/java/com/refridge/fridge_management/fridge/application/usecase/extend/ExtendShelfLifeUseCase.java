package com.refridge.fridge_management.fridge.application.usecase.extend;

import com.refridge.fridge_management.fridge.application.service.FridgeItemHistoryQueryService;
import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemContext;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import com.refridge.fridge_management.fridge.infrastructure.advisor.ShelfLifeAdvisorPort;
import com.refridge.fridge_management.fridge.infrastructure.advisor.ShelfLifeRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 소비기한 연장 유스케이스 (UC7).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * POST /fridge/items/{id}/extend
 *   ↓
 * [additionalDays 미제공 시]
 *   FridgeItemHistoryQueryService.buildContext(item)
 *     → FridgeItemContext 조립 (아이템 + 이력)
 *   ShelfLifeAdvisorPort.recommend(context)
 *     → LLM이 개봉 여부, 보관 환경, 연장 이력 종합 판단
 *     → 추천 연장 일수 반환
 * [additionalDays 제공 시]
 *   사용자 지정 일수 사용
 *   ↓
 * Fridge.extend(fridgeItemId, additionalDays, today)
 *   → 소비기한 미설정 아이템 방어
 *   → 임박(7일 이내) or 초과 아이템 한정 검증
 *   → ExpirationInfo.extend(days) — 연장 횟수 제한 없음
 *   → FridgeItemShelfLifeExtendedEvent 등록 (extensionCount 포함)
 *   ↓
 * FridgeOutboxAppender(BEFORE_COMMIT) → fridge_pending_event INSERT
 * FridgeItemHistoryAppender(BEFORE_COMMIT) → SHELF_EXTENDED 이력 INSERT
 * </pre>
 *
 * <h2>연장 횟수 제한 없음</h2>
 * 냉동 미개봉 상태로 오래 보관한 경우, 소비기한이 지났더라도
 * 사용자가 실제 상태를 판단해 반복 연장할 수 있다.
 * 연장 여부는 {@code ExpirationInfo.isShelfLifeExtended()}로 확인 가능.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class ExtendShelfLifeUseCase {

    private final FridgeRepository fridgeRepository;
    private final FridgeItemHistoryQueryService historyQueryService;
    private final ShelfLifeAdvisorPort shelfLifeAdvisorPort;

    /**
     * 소비기한 연장 실행.
     *
     * @param memberId       회원 ID
     * @param fridgeItemId   연장할 아이템 ID
     * @param additionalDays 연장 일수 (null이면 LLM 어드바이저 추천값 사용)
     * @return 연장 결과 (추천 정보 포함)
     */
    @Transactional
    public ExtendShelfLifeResult execute(String memberId, String fridgeItemId, Integer additionalDays) {
        Fridge fridge = requireFridge(memberId);
        LocalDate today = LocalDate.now();

        FridgeItem item = fridge.getSections().values().stream()
                .flatMap(s -> s.allItems().stream())
                .filter(i -> i.getFridgeItemId().equals(fridgeItemId) && i.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "ACTIVE 아이템을 찾을 수 없습니다. fridgeItemId=" + fridgeItemId));

        // 연장 일수 결정
        ShelfLifeRecommendation recommendation = null;
        int days;
        if (additionalDays != null) {
            days = additionalDays;
        } else {
            // LLM 어드바이저: 이력 포함 컨텍스트 기반 추천
            FridgeItemContext context = historyQueryService.buildContext(item);
            recommendation = shelfLifeAdvisorPort.recommend(context);
            days = recommendation.recommendedDays();
        }

        // 연장 전 소비기한 (결과 DTO용)
        LocalDate beforeExpiresAt = item.getExpirationInfo().getExpiresAt();

        // 도메인 메서드 호출 (미설정·임박·초과 검증은 도메인 책임)
        fridge.extend(fridgeItemId, days, today);
        fridgeRepository.save(fridge);

        // 연장 후 상태 (FridgeItem은 save 후 갱신됨)
        int newExtensionCount = item.getExpirationInfo().getExtensionCount();
        LocalDate newExpiresAt = item.getExpirationInfo().getExpiresAt();

        return new ExtendShelfLifeResult(
                fridgeItemId,
                beforeExpiresAt,
                newExpiresAt,
                days,
                newExtensionCount,
                recommendation
        );
    }

    private Fridge requireFridge(String memberId) {
        return fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
    }
}