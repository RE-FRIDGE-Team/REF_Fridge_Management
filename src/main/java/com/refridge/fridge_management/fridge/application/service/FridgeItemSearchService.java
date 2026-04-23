package com.refridge.fridge_management.fridge.application.service;

import com.refridge.fridge_management.fridge.application.query.FridgeItemResult;
import com.refridge.fridge_management.fridge.application.query.FridgeItemSearchCondition;
import com.refridge.fridge_management.fridge.domain.repository.FridgeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 냉장고 아이템 동적 검색 서비스 (CQRS Query 측).
 *
 * <h2>역할</h2>
 * QueryDSL 기반의 동적 필터/페이지네이션 검색을 Application 레이어에서 조율한다.
 * 컨트롤러는 이 서비스만 호출하고, Repository 직접 의존은 금지한다.
 *
 * <h2>페이지네이션 반환</h2>
 * {@code Page<FridgeItemResult>}를 반환해 컨트롤러에서 {@code totalElements}, {@code totalPages}를
 * 그대로 클라이언트에 전달할 수 있도록 한다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeItemSearchCondition
 * @see com.refridge.fridge_management.fridge.domain.repository.FridgeItemQueryRepository
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeItemSearchService {

    private final FridgeItemRepository fridgeItemRepository;

    /**
     * 아이템 동적 검색.
     *
     * @param condition 검색 조건 (구역, 상태, 키워드, 페이지, 정렬)
     * @return 페이지네이션된 아이템 결과
     */
    public Page<FridgeItemResult> search(FridgeItemSearchCondition condition) {
        Pageable pageable = PageRequest.of(
                condition.page(),
                condition.size(),
                Sort.by(
                        Sort.Direction.fromString(condition.sortDir()),
                        condition.sortBy()
                )
        );

        return fridgeItemRepository.searchItems(
                condition.fridgeId(),
                condition.sectionType(),
                condition.status(),
                condition.keyword(),
                pageable
        ).map(FridgeItemResult::from);
    }
}
