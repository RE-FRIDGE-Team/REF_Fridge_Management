-- ============================================================
-- V6: fridge_item_history (아이템 이력 테이블) 생성
--
-- 목적:
--   FridgeItem의 주요 상태 변화 이력을 기록한다.
--   LLM 소비기한 추천 어드바이저(ShelfLifeAdvisorPort)가 이 이력을 활용해
--   개봉 여부 추정, 보관 환경 변화 등을 판단한다.
--
-- 기록 대상 이벤트 (FridgeItemHistoryAppender):
--   ADDED          : FridgeFillCompletedEvent
--   MOVED          : FridgeItemMovedEvent
--   PORTIONED      : FridgeItemPortionedEvent        (개봉 추정)
--   COOKED         : FridgeItemCookedEvent 재료별     (개봉 추정)
--   SHELF_EXTENDED : FridgeItemShelfLifeExtendedEvent
--
-- 제외 이벤트:
--   CONSUMED, DISPOSED — terminal 이벤트라 이력 조회 자체가 의미 없음
--   FridgeItemExpirationRegisteredEvent — 단순 수정, 이력 불필요
--
-- payload JSON 예시:
--   ADDED:          {}
--   MOVED:          { "from": "FREEZER", "to": "REFRIGERATED", "wasUpwardMove": true }
--   PORTIONED:      { "portionCount": 3 }
--   COOKED:         { "cookedFridgeItemId": "uuid" }
--   SHELF_EXTENDED: { "additionalDays": 7, "newExpiresAt": "2026-06-01", "extensionCount": 2 }
--
-- 개봉 추정 판단:
--   이력 중 MOVED / PORTIONED / COOKED 가 하나라도 있으면
--   FridgeItemContext.estimatedOpened = true
--
-- 작성: 승훈 / 2026-04-26
-- ============================================================

CREATE TABLE fridge_item_history
(
    history_id     VARCHAR(36)  NOT NULL,
    fridge_item_id VARCHAR(36)  NOT NULL,
    member_id      VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(30)  NOT NULL,
    payload        JSONB,
    occurred_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_fridge_item_history PRIMARY KEY (history_id)
);

COMMENT ON TABLE  fridge_item_history              IS 'FridgeItem 상태 변화 이력 테이블 (LLM 소비기한 추천용)';
COMMENT ON COLUMN fridge_item_history.history_id   IS '이력 고유 ID (UUID)';
COMMENT ON COLUMN fridge_item_history.fridge_item_id IS 'FridgeItem ID';
COMMENT ON COLUMN fridge_item_history.member_id    IS '회원 ID (역정규화 — JOIN 없이 집계 가능)';
COMMENT ON COLUMN fridge_item_history.event_type   IS 'ADDED / MOVED / PORTIONED / COOKED / SHELF_EXTENDED';
COMMENT ON COLUMN fridge_item_history.payload      IS '이벤트 유형별 세부 정보';
COMMENT ON COLUMN fridge_item_history.occurred_at  IS '이벤트 발생 시각';

-- LLM 어드바이저 컨텍스트 조회용 (fridge_item_id 기준, 시간순)
CREATE INDEX idx_fih_fridge_item_id
    ON fridge_item_history (fridge_item_id, occurred_at);

-- 회원 단위 이력 집계용
CREATE INDEX idx_fih_member_id
    ON fridge_item_history (member_id, occurred_at);
