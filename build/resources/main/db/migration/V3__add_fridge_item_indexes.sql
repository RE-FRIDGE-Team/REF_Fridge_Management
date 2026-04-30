-- ============================================================
-- V3: fridge_item 성능 인덱스 추가
--
-- 목적:
--   핵심 쿼리 패턴에 맞는 복합 인덱스를 테이블 생성(V2)과 분리하여 추가한다.
--
-- 인덱스 전략:
--   [1] idx_fi_fridge_status  (fridge_id, status)
--       쿼리: Fridge.requireActiveItem() — fridge_id + ACTIVE 필터
--
--   [2] idx_fi_expires_at  (expires_at)
--       쿼리: 배치 스케줄러 임박 알림 — 전체 서버 expires_at BETWEEN
--       V5에서 expires_at이 nullable이 되므로 null 값은 인덱스에서 자동 제외됨
--
--   [3] idx_fi_member_status  (member_id, status, expires_at)
--       쿼리: Saving BC 집계 — 회원별 기간 내 소비/폐기 아이템
--
-- 작성: 승훈 / 2025-06-01
-- ============================================================

SET search_path TO fridge_schema;

CREATE INDEX idx_fi_fridge_status
    ON fridge_item (fridge_id, status);

CREATE INDEX idx_fi_expires_at
    ON fridge_item (expires_at);

CREATE INDEX idx_fi_member_status
    ON fridge_item (member_id, status, expires_at);