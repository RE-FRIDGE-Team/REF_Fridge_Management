package com.refridge.fridge_management.fridge.application.service;

import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemContext;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistory;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 냉장고 아이템 이력 조회 Application Service.
 *
 * <h2>DDD + Hexagonal 위치</h2>
 * Application 레이어에 위치한다.
 * UseCase(ExtendShelfLifeUseCase 등)는 Repository를 직접 의존하지 않고
 * 이 서비스를 통해 이력 데이터에 접근한다.
 *
 * <h2>역할</h2>
 * <ul>
 *   <li>이력 목록 조회 및 {@code FridgeItemContext} 조립</li>
 *   <li>개봉 추정, 총 연장 일수 등 이력 기반 파생 정보 제공</li>
 *   <li>향후 이력 기반 통계, 패턴 분석 등 확장 시 이 서비스에 메서드를 추가</li>
 * </ul>
 *
 * <h2>트랜잭션</h2>
 * 읽기 전용. UseCase 트랜잭션과 별도로 실행되어도 무방하다.
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FridgeItemContext
 * @see com.refridge.fridge_management.fridge.application.usecase.extend.ExtendShelfLifeUseCase
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeItemHistoryQueryService {

    private final FridgeItemHistoryRepository historyRepository;

    /**
     * 아이템 이력 전체 조회 (오래된 순).
     *
     * @param fridgeItemId 아이템 ID
     * @return 이력 목록
     */
    public List<FridgeItemHistory> findAllHistory(String fridgeItemId) {
        return historyRepository.findAllByFridgeItemId(fridgeItemId);
    }

    /**
     * FridgeItem + 이력을 조합해 LLM 어드바이저에 전달할 {@code FridgeItemContext}를 조립한다.
     *
     * <h3>호출 시점</h3>
     * {@code ExtendShelfLifeUseCase}에서 어드바이저 호출 직전에 사용.
     *
     * @param item 소비기한 연장 대상 FridgeItem
     * @return FridgeItemContext (이력 포함)
     */
    public FridgeItemContext buildContext(FridgeItem item) {
        List<FridgeItemHistory> history = historyRepository.findAllByFridgeItemId(item.getFridgeItemId());
        return FridgeItemContext.of(item, history);
    }

    /**
     * 아이템이 개봉된 것으로 추정되는지 여부.
     * 이력 중 MOVED / PORTIONED / COOKED 가 하나라도 있으면 개봉 추정.
     *
     * @param fridgeItemId 아이템 ID
     */
    public boolean isEstimatedOpened(String fridgeItemId) {
        return historyRepository.findAllByFridgeItemId(fridgeItemId)
                .stream()
                .anyMatch(FridgeItemHistory::impliesOpened);
    }
}