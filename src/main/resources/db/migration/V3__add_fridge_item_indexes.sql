-- ============================================================
-- V3: fridge_item 성능 인덱스 추가
--
-- 목적:
--   핵심 쿼리 패턴에 맞는 복합 인덱스를 테이블 생성(V2)과 분리하여 추가한다.
--   인덱스만 변경이 필요할 때 V2를 건드리지 않고 새 버전 파일로 관리 가능.
--
-- 인덱스 전략 근거:
--
--   [1] idx_fi_fridge_status  (fridge_id, status)
--       쿼리: Fridge.requireActiveItem() — 특정 냉장고 ACTIVE 아이템 탐색
--       패턴: WHERE fridge_id = ? AND status = 'ACTIVE'  ← 가장 빈번
--       선택도: fridge_id(높음) → status(중간) 순으로 배치
--
--   [2] idx_fi_expires_at  (expires_at)
--       쿼리: 배치 스케줄러 임박 알림 — 전체 서버 범위 expires_at BETWEEN
--       패턴: WHERE status = 'ACTIVE' AND expires_at BETWEEN ? AND ?
--       참고: status 앞에 expires_at을 두면 range scan 이후 status 필터 불가.
--             배치 쿼리는 전체 서버 스캔이므로 단독 인덱스가 적절.
--
--   [3] idx_fi_member_status  (member_id, status, expires_at)
--       쿼리: Saving BC 집계 — 회원별 기간 내 소비/폐기 아이템
--       패턴: WHERE member_id = ? AND status IN (?, ?) AND expires_at BETWEEN ? AND ?
--       컬럼 순서 원칙:
--         ① member_id — 등치(=), 선택도 가장 높음 → 맨 앞
--         ② status    — IN/등치, 중간 선택도
--         ③ expires_at — range(BETWEEN), 항상 맨 마지막 (range 이후 인덱스 활용 불가)
--
-- 작성: 승훈 / 2025-06-01
-- ============================================================

USE fridge_schema;

-- [1] AR 내부 탐색 (consume/dispose/move 등 모든 상태 변경 연산)
CREATE INDEX idx_fi_fridge_status
    ON fridge_item (fridge_id, status)
    COMMENT 'Fridge 도메인 메서드 — fridge_id + status 필터';

-- [2] 배치 스케줄러 유통기한 임박 전체 스캔
CREATE INDEX idx_fi_expires_at
    ON fridge_item (expires_at)
    COMMENT '배치 임박 알림 — 전체 서버 expires_at BETWEEN 스캔';

-- [3] Saving BC 회원별 통계 집계
CREATE INDEX idx_fi_member_status
    ON fridge_item (member_id, status, expires_at)
    COMMENT 'Saving BC 집계 — member_id(등치) + status(IN) + expires_at(range)';
