package com.refridge.fridge_management.fridge.application.usecase.expiration;

import com.refridge.fridge_management.fridge.domain.Fridge;
import com.refridge.fridge_management.fridge.domain.repository.FridgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 소비기한 등록 유스케이스.
 *
 * <h2>역할</h2>
 * 냉장고 채우기(인식) 후 소비기한을 별도로 등록한다.
 * 채우기 시점에는 소비기한을 입력받지 않으므로,
 * 사용자가 실물 제품의 표기를 확인한 뒤 별도로 입력하는 흐름이다.
 *
 * <h2>수정 허용</h2>
 * 이미 소비기한이 등록된 아이템도 수정을 허용한다 (입력 오류 정정).
 * 연장 이력({@code extensionCount})은 초기화되지 않는다.
 *
 * <h2>이벤트</h2>
 * {@code FridgeItemExpirationRegisteredEvent}가 발행된다.
 * Outbox 대상이 아니며, 이력 기록 및 감사 목적에 한정된다.
 * ({@code FridgeItemHistoryAppender}는 이 이벤트를 수신하지 않는다 — 단순 등록이므로 이력 불필요)
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Service
@RequiredArgsConstructor
public class RegisterExpirationUseCase {

    private final FridgeRepository fridgeRepository;

    /**
     * 소비기한 등록 (또는 수정).
     *
     * @param memberId     회원 ID
     * @param fridgeItemId 소비기한을 등록할 아이템 ID
     * @param expiresAt    등록할 소비기한
     * @throws IllegalArgumentException 냉장고 또는 ACTIVE 아이템을 찾지 못한 경우
     */
    @Transactional
    public void execute(String memberId, String fridgeItemId, LocalDate expiresAt) {
        Fridge fridge = fridgeRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "냉장고를 찾을 수 없습니다. memberId=" + memberId));
        fridge.registerExpiration(fridgeItemId, expiresAt);
        fridgeRepository.save(fridge);
    }
}