package com.refridge.fridge_management.fridge.application.service;

import com.refridge.fridge_management.fridge.application.query.FridgeResult;
import com.refridge.fridge_management.fridge.application.query.FridgeSectionResult;
import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 냉장고 읽기 전용 서비스 (CQRS Query 측).
 *
 * <h2>읽기 전용 트랜잭션</h2>
 * {@code @Transactional(readOnly = true)}: Hibernate의 FlushMode를 MANUAL로 설정해
 * Dirty Checking을 생략하고 읽기 성능을 향상시킨다.
 * 또한 DB Replica로의 라우팅 힌트가 되어 향후 Read Replica 도입 시 자동 분기된다.
 *
 * <h2>왜 별도 서비스인가?</h2>
 * 쓰기 유스케이스(FillFridgeUseCase, ConsumeItemUseCase 등)와 분리함으로써
 * 읽기/쓰기 모델 각각을 독립적으로 최적화할 수 있다.
 * 현재는 JPA를 통한 단일 DB이지만, 향후 CQRS 완전 분리 시에도 이 인터페이스가 경계가 된다.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeItemSearchService
 * @see FridgeResult
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeViewService {

    private final FridgeRepository fridgeRepository;

    /**
     * 냉장고 전체 조회.
     * sections(EAGER) + items(LAZY → JOIN FETCH)를 한 번의 쿼리로 로딩.
     *
     * @param memberId 회원 ID
     * @return 냉장고 전체 결과 (총가치, 구역별 아이템, 임박 카운트 포함)
     * @throws IllegalArgumentException 해당 회원의 냉장고가 없을 경우
     */
    public FridgeResult getFridge(String memberId) {
        Fridge fridge = fridgeRepository.findByMemberIdWithItems(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
        return FridgeResult.from(fridge);
    }

    /**
     * 특정 구역 조회.
     * 전체 냉장고를 로드하지 않고 해당 구역만 반환.
     *
     * @param memberId    회원 ID
     * @param sectionType 조회할 구역
     * @return 해당 구역의 ACTIVE 아이템 목록
     * @throws IllegalArgumentException 냉장고 또는 구역이 없을 경우
     */
    public FridgeSectionResult getFridgeSection(String memberId, SectionType sectionType) {
        Fridge fridge = fridgeRepository.findByMemberIdWithItems(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
        return FridgeSectionResult.from(fridge.getSection(sectionType));
    }
}
