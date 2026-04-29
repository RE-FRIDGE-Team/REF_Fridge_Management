package com.refridge.fridge_management.fridge.controller;

import com.refridge.fridge_management.fridge.application.query.FridgeItemResult;
import com.refridge.fridge_management.fridge.application.usecase.consume.ConsumeItemUseCase;
import com.refridge.fridge_management.fridge.application.usecase.cook.CookUseCase;
import com.refridge.fridge_management.fridge.application.usecase.dispose.DisposeItemUseCase;
import com.refridge.fridge_management.fridge.application.usecase.expiration.RegisterExpirationUseCase;
import com.refridge.fridge_management.fridge.application.usecase.extend.ExtendShelfLifeResult;
import com.refridge.fridge_management.fridge.application.usecase.extend.ExtendShelfLifeUseCase;
import com.refridge.fridge_management.fridge.application.usecase.fill.FillFridgeResult;
import com.refridge.fridge_management.fridge.application.usecase.fill.FillFridgeUseCase;
import com.refridge.fridge_management.fridge.application.usecase.move.MoveItemResult;
import com.refridge.fridge_management.fridge.application.usecase.move.MoveItemUseCase;
import com.refridge.fridge_management.fridge.application.usecase.portion.PortionItemResult;
import com.refridge.fridge_management.fridge.application.usecase.portion.PortionItemUseCase;
import com.refridge.fridge_management.fridge.controller.request.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 냉장고 변경(쓰기) REST 컨트롤러.
 *
 * <h2>책임</h2>
 * <ul>
 *   <li>HTTP 요청 파라미터를 검증하고 Request DTO → Command 변환 후 UseCase에 위임한다.</li>
 *   <li>비즈니스 로직은 일절 포함하지 않는다.</li>
 *   <li>조회(GET)는 {@link FridgeController}가 담당한다.</li>
 * </ul>
 *
 * <h2>인증</h2>
 * 현재는 {@code X-Member-Id} 헤더로 회원 ID를 전달한다.
 * JWT 인증 도입 후 Security Context에서 추출하는 방식으로 교체 예정.
 *
 * <h2>엔드포인트</h2>
 * <pre>
 * POST   /fridge/items                    → 냉장고 채우기 (배치)
 * POST   /fridge/items/{id}/expiration    → 소비기한 등록
 * POST   /fridge/items/{id}/consume       → 먹기
 * POST   /fridge/items/{id}/dispose       → 폐기
 * PATCH  /fridge/items/{id}/section       → 구역 이동
 * POST   /fridge/items/{id}/portion       → 소분
 * POST   /fridge/items/{id}/extend        → 소비기한 연장
 * POST   /fridge/cook                     → 즉석 요리
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 * @see FridgeController
 */
@RestController
@RequestMapping("/fridge")
@RequiredArgsConstructor
public class FridgeMutationController {

    private final FillFridgeUseCase fillFridgeUseCase;
    private final RegisterExpirationUseCase registerExpirationUseCase;
    private final ConsumeItemUseCase consumeItemUseCase;
    private final DisposeItemUseCase disposeItemUseCase;
    private final MoveItemUseCase moveItemUseCase;
    private final PortionItemUseCase portionItemUseCase;
    private final ExtendShelfLifeUseCase extendShelfLifeUseCase;
    private final CookUseCase cookUseCase;

    // ── POST /fridge/items ────────────────────────────────────────────

    /**
     * 냉장고 채우기 (배치).
     *
     * <h3>처리 흐름</h3>
     * Recognition 결과 수정 확정 → 이 API 호출 →
     * {@code FillFridgeUseCase} → Outbox → Feedback BC diff 판정
     *
     * <h3>소비기한</h3>
     * 채우기 시점에는 소비기한을 입력받지 않는다.
     * 이후 {@code POST /fridge/items/{id}/expiration}으로 별도 등록.
     *
     * @param memberId 회원 ID (헤더)
     * @param req      채우기 배치 요청
     * @return 생성된 FridgeItem 목록
     */
    @PostMapping("/items")
    public ResponseEntity<List<FillFridgeResult>> fill(
            @RequestHeader("X-Member-Id") String memberId,
            @Valid @RequestBody BatchAddItemsRequest req
    ) {
        List<FillFridgeResult> results = fillFridgeUseCase.execute(memberId, req.toCommands());
        return ResponseEntity.ok(results);
    }

    // ── POST /fridge/items/{id}/expiration ───────────────────────────

    /**
     * 소비기한 등록 (채우기 이후 별도 입력).
     *
     * <h3>정책</h3>
     * 이미 소비기한이 등록된 아이템도 수정을 허용한다 (입력 오류 정정).
     * 연장 이력({@code extensionCount})은 초기화되지 않는다.
     *
     * @param memberId 회원 ID
     * @param itemId   소비기한을 등록할 FridgeItem ID
     * @param req      소비기한 등록 요청
     */
    @PostMapping("/items/{id}/expiration")
    public ResponseEntity<Void> registerExpiration(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId,
            @Valid @RequestBody RegisterExpirationRequest req
    ) {
        registerExpirationUseCase.execute(memberId, itemId, req.expiresAt());
        return ResponseEntity.noContent().build();
    }

    // ── POST /fridge/items/{id}/consume ──────────────────────────────

    /**
     * 먹기.
     *
     * <h3>이벤트</h3>
     * {@code FridgeItemConsumedEvent} → Outbox → {@code fridge:consumed} →
     * Saving BC (가공식품이면 배달가와 비교해 절약액 산출)
     *
     * @param memberId 회원 ID
     * @param itemId   먹을 FridgeItem ID
     */
    @PostMapping("/items/{id}/consume")
    public ResponseEntity<Void> consume(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId
    ) {
        consumeItemUseCase.execute(memberId, itemId);
        return ResponseEntity.noContent().build();
    }

    // ── POST /fridge/items/{id}/dispose ──────────────────────────────

    /**
     * 폐기.
     *
     * <h3>이벤트</h3>
     * {@code FridgeItemDisposedEvent} → Outbox → {@code fridge:disposed} →
     * Saving BC ("낭비된 식비" 누적)
     *
     * @param memberId 회원 ID
     * @param itemId   폐기할 FridgeItem ID
     */
    @PostMapping("/items/{id}/dispose")
    public ResponseEntity<Void> dispose(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId
    ) {
        disposeItemUseCase.execute(memberId, itemId);
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /fridge/items/{id}/section ─────────────────────────────

    /**
     * 구역 이동.
     *
     * <h3>상행성 이동 경고</h3>
     * 응답의 {@code wasUpwardMove=true}이면 클라이언트가 경고 UI를 표시한다.
     * (냉동→냉장, 냉동→상온, 냉장→상온 방향)
     *
     * @param memberId 회원 ID
     * @param itemId   이동할 FridgeItem ID
     * @param req      목적지 구역
     * @return 이동 결과 (wasUpwardMove 포함)
     */
    @PatchMapping("/items/{id}/section")
    public ResponseEntity<MoveItemResult> move(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId,
            @Valid @RequestBody MoveItemRequest req
    ) {
        MoveItemResult result = moveItemUseCase.execute(memberId, itemId, req.targetSection());
        return ResponseEntity.ok(result);
    }

    // ── POST /fridge/items/{id}/portion ──────────────────────────────

    /**
     * 소분.
     *
     * <h3>정책</h3>
     * 원본 아이템은 {@code PORTIONED_OUT} 상태로 전환되고,
     * 자식 아이템 N개가 생성된다. 가격은 비례 분할된다.
     *
     * @param memberId 회원 ID
     * @param itemId   소분할 FridgeItem ID
     * @param req      소분 수 (2 이상)
     * @return 생성된 자식 아이템 목록
     */
    @PostMapping("/items/{id}/portion")
    public ResponseEntity<List<PortionItemResult>> portion(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId,
            @Valid @RequestBody PortionRequest req
    ) {
        List<PortionItemResult> results = portionItemUseCase.execute(memberId, itemId, req.portionCount());
        return ResponseEntity.ok(results);
    }

    // ── POST /fridge/items/{id}/extend ───────────────────────────────

    /**
     * 소비기한 연장.
     *
     * <h3>정책</h3>
     * <ul>
     *   <li>소비기한 임박(7일 이내) 또는 초과 아이템에만 적용 가능.</li>
     *   <li>연장 횟수 제한 없음. {@code isShelfLifeExtended()}로 연장 여부 확인 가능.</li>
     *   <li>{@code additionalDays=null}이면 LLM 어드바이저가 이력 기반으로 추천.</li>
     * </ul>
     *
     * <h3>UI 권장 흐름</h3>
     * LLM 추천값을 화면에 표시 → 사용자가 수정하거나 그대로 확정 →
     * 추천값 그대로면 {@code null}, 수정 시 해당 값을 전송.
     *
     * @param memberId 회원 ID
     * @param itemId   연장할 FridgeItem ID
     * @param req      연장 일수 (null이면 LLM 추천 경로)
     * @return 연장 결과 (추천 정보 포함)
     */
    @PostMapping("/items/{id}/extend")
    public ResponseEntity<ExtendShelfLifeResult> extend(
            @RequestHeader("X-Member-Id") String memberId,
            @PathVariable("id") String itemId,
            @Valid @RequestBody ExtendRequest req
    ) {
        ExtendShelfLifeResult result = extendShelfLifeUseCase.execute(
                memberId, itemId, req.additionalDays());
        return ResponseEntity.ok(result);
    }

    // ── POST /fridge/cook ────────────────────────────────────────────

    /**
     * 즉석 요리.
     *
     * <h3>처리 흐름</h3>
     * 재료 다중 선택 → UsageSpec으로 사용량 명세 →
     * 요리명·N인분·보관 구역 입력 → 재료 일괄 소비 + 요리 결과 아이템 생성.
     *
     * <h3>사용량 명세 (UsageSpec)</h3>
     * <ul>
     *   <li>{@code "type": "FULL"} — 전량 사용</li>
     *   <li>{@code "type": "QUANTITY", "amount": 200, "unit": "G"} — 200g 사용</li>
     *   <li>{@code "type": "RATIO", "ratio": 0.5} — 절반 사용</li>
     * </ul>
     * v3에서는 부분 사용도 전량 소비로 처리됨 (소분 후 요리 흐름 향후 고도화).
     *
     * <h3>이벤트</h3>
     * {@code FridgeItemCookedEvent} → Outbox → {@code fridge:cooked} →
     * Saving BC (재료별 절약액 집계)
     *
     * @param memberId 회원 ID
     * @param req      요리 요청 (재료 목록, 요리명, 인분, 소비기한, 보관 구역)
     * @return 생성된 요리 결과 FridgeItem
     */
    @PostMapping("/cook")
    public ResponseEntity<FridgeItemResult> cook(
            @RequestHeader("X-Member-Id") String memberId,
            @Valid @RequestBody CookRequest req
    ) {
        FridgeItemResult result = cookUseCase.execute(memberId, req.toCommand());
        return ResponseEntity.ok(result);
    }
}