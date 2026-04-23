package com.refridge.fridge_management.fridge.controller;

import com.refridge.fridge_management.fridge.application.query.*;
import com.refridge.fridge_management.fridge.application.service.FridgeItemSearchService;
import com.refridge.fridge_management.fridge.application.service.FridgeViewService;
import com.refridge.fridge_management.fridge.domain.vo.ItemStatus;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 냉장고 조회 REST 컨트롤러.
 *
 * <h2>책임</h2>
 * <ul>
 *   <li>HTTP 요청 파라미터를 검증하고 Application 레이어 서비스에 위임한다.</li>
 *   <li>비즈니스 로직은 일절 포함하지 않는다.</li>
 *   <li>memberId는 추후 JWT 토큰에서 추출하는 것으로 교체 예정.
 *       현재는 Header로 전달받아 개발 편의성 확보.</li>
 * </ul>
 *
 * <h2>엔드포인트</h2>
 * <pre>
 * GET  /fridge                        → 냉장고 전체 조회 (구역별 아이템 포함)
 * GET  /fridge/sections/{sectionType} → 특정 구역 조회
 * GET  /fridge/items                  → 아이템 동적 검색 (필터 + 페이지네이션)
 * </pre>
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeViewService
 * @see FridgeItemSearchService
 */
@RestController
@RequestMapping("/fridge")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeViewService fridgeViewService;
    private final FridgeItemSearchService fridgeItemSearchService;

    // ── GET /fridge ───────────────────────────────────────────────────────

    /**
     * 냉장고 전체 조회.
     * 총가치, 구역별 아이템 목록, 유통기한 임박 카운트를 포함한다.
     *
     * @param memberId 회원 ID (Header — JWT 도입 전 임시)
     * @return 냉장고 전체 결과
     */
    @GetMapping
    public ResponseEntity<FridgeResult> getFridge(
            @RequestHeader("X-Member-Id") String memberId
    ) {
        return ResponseEntity.ok(fridgeViewService.getFridge(memberId));
    }

    // ── GET /fridge/sections/{sectionType} ───────────────────────────────

    /**
     * 특정 구역 조회.
     * 그리드/리스트 뷰에서 구역 탭을 전환할 때 사용.
     *
     * @param memberId    회원 ID
     * @param sectionType 구역 (ROOM_TEMPERATURE | REFRIGERATED | FREEZER)
     * @return 해당 구역의 ACTIVE 아이템 목록
     */
    @GetMapping("/sections/{sectionType}")
    public ResponseEntity<FridgeSectionResult> getFridgeSection(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable SectionType sectionType
    ) {
        return ResponseEntity.ok(fridgeViewService.getFridgeSection(memberId, sectionType));
    }

    // ── GET /fridge/items ─────────────────────────────────────────────────

    /**
     * 아이템 동적 검색.
     * 구역 필터, 상태 필터, 식품명 키워드, 페이지네이션, 정렬을 지원한다.
     *
     * <h3>요청 파라미터</h3>
     * <ul>
     *   <li>{@code fridgeId}     : 냉장고 ID (필수)</li>
     *   <li>{@code sectionType}  : 구역 필터 (선택 — 미입력 시 전체)</li>
     *   <li>{@code status}       : 상태 필터 (선택 — 미입력 시 ACTIVE)</li>
     *   <li>{@code keyword}      : 식품명 검색 (선택)</li>
     *   <li>{@code page}         : 페이지 번호 (기본값 0)</li>
     *   <li>{@code size}         : 페이지 크기 (기본값 20)</li>
     *   <li>{@code sortBy}       : 정렬 기준 (기본값 expiresAt)</li>
     *   <li>{@code sortDir}      : 정렬 방향 asc|desc (기본값 asc)</li>
     * </ul>
     *
     * @return 페이지네이션된 아이템 결과
     */
    @GetMapping("/items")
    public ResponseEntity<Page<FridgeItemResult>> searchItems(
            @RequestParam String fridgeId,
            @RequestParam(required = false) SectionType sectionType,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "expiresAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        FridgeItemSearchCondition condition = FridgeItemSearchCondition.of(
                fridgeId, sectionType, status, keyword, page, size, sortBy, sortDir
        );
        return ResponseEntity.ok(fridgeItemSearchService.search(condition));
    }
}
