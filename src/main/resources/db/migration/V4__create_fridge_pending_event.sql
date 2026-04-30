-- ============================================================
-- V4: fridge_pending_event (Outbox 테이블) 생성
--
-- 목적:
--   Fridge BC Outbox 패턴 구현을 위한 이벤트 발행 대기 테이블.
--   도메인 이벤트를 Redis Stream으로 발행하기 전 DB에 먼저 저장해
--   비즈니스 트랜잭션과 이벤트 발행의 원자성을 보장한다.
--
-- 상태 전이:
--   PENDING → PUBLISHED  (정상 발행)
--   PENDING → FAILED     (발행 실패, retry_count 증가)
--   FAILED  → PENDING    (FridgeOutboxRelayer.retryFailed()가 재시도)
--
-- Stream Key 매핑:
--   fridge:consumed       → Saving BC (saving-group)
--   fridge:disposed       → Saving BC (saving-group)
--   fridge:cooked         → Saving BC (saving-group)
--   fridge:moved          → (future) Statistics (stats-group)
--   fridge:portioned      → (future) Statistics (stats-group)
--   fridge:extended       → (future) Statistics (stats-group)
--   fridge:near-expiry    → noti_server (noti-near-expiry-group)
--   fridge:fill-completed → Feedback BC (feedback-fill-group)
--
-- 제외 이벤트:
--   FridgeItemExpirationRegisteredEvent — Outbox 발행 대상 아님
--   (FridgeOutboxAppender에서 isOutboxTarget()으로 skip 처리)
--
-- 작성: 승훈 / 2026-04-26
-- ============================================================

CREATE TABLE fridge_pending_event
(
    event_id       VARCHAR(36)   NOT NULL,
    aggregate_type VARCHAR(40)   NOT NULL,
    aggregate_id   VARCHAR(36)   NOT NULL,
    event_type     VARCHAR(80)   NOT NULL,
    stream_key     VARCHAR(80)   NOT NULL,
    payload        JSONB         NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count    INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL,
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_fridge_pending_event PRIMARY KEY (event_id)
);

COMMENT ON TABLE  fridge_pending_event              IS 'Fridge BC Outbox 이벤트 테이블';
COMMENT ON COLUMN fridge_pending_event.event_id     IS '이벤트 고유 ID (UUID)';
COMMENT ON COLUMN fridge_pending_event.aggregate_type IS '항상 FRIDGE';
COMMENT ON COLUMN fridge_pending_event.aggregate_id IS 'fridgeItemId (조회·감사용)';
COMMENT ON COLUMN fridge_pending_event.event_type   IS '이벤트 클래스 단순명 (ex: FridgeItemConsumedEvent)';
COMMENT ON COLUMN fridge_pending_event.stream_key   IS 'Redis Stream key (ex: fridge:consumed)';
COMMENT ON COLUMN fridge_pending_event.payload      IS '이벤트 record JSON';
COMMENT ON COLUMN fridge_pending_event.status       IS 'PENDING / PUBLISHED / FAILED';
COMMENT ON COLUMN fridge_pending_event.retry_count  IS '발행 실패 누적 횟수';
COMMENT ON COLUMN fridge_pending_event.last_error   IS '마지막 실패 오류 메시지';

-- PENDING 이벤트 폴링용 인덱스 (FridgeOutboxRelayer가 created_at 오름차순으로 처리)
CREATE INDEX idx_fridge_pending_event_status_created
    ON fridge_pending_event (status, created_at);
