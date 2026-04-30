-- ============================================================
-- V5: fridge_item 소비기한 컬럼 변경 (v3 → v4)
--
-- 변경 이유:
--   v3: shelf_life_extended BOOLEAN (1회 연장 한정 정책)
--   v4: extension_count INT (연장 횟수 무제한, 연장 여부는 extension_count > 0으로 판단)
--
--   사용자가 냉동 미개봉 등으로 소비 가능 상태를 직접 판단해
--   반복 연장할 수 있도록 정책 변경.
--   연장 이력은 isShelfLifeExtended() (extension_count > 0) 로 확인 가능.
--
-- 추가 변경:
--   expires_at: NOT NULL → NULL 허용
--   채우기 시점에 소비기한을 입력받지 않으므로 unset 상태(null) 허용.
--   이후 POST /fridge/items/{id}/expiration 으로 별도 등록.
--
--   manufactured_at 컬럼 제거:
--   FillFridgeCommand에서 제조일 필드를 제거함에 따라 불필요.
--
-- 데이터 마이그레이션:
--   original_expires_at IS NOT NULL인 row는 이미 한 번 이상 연장된 아이템이므로
--   extension_count = 1로 설정.
--
-- 작성: 승훈 / 2026-04-26
-- ============================================================

-- shelf_life_extended(BOOLEAN) 제거 → extension_count(INT) 추가
ALTER TABLE fridge_item
    DROP COLUMN shelf_life_extended,
    DROP COLUMN manufactured_at,
    ADD COLUMN  extension_count INT NOT NULL DEFAULT 0,
    ALTER COLUMN expires_at DROP NOT NULL;

COMMENT ON COLUMN fridge_item.extension_count IS '소비기한 누적 연장 횟수 (0=미연장, 1 이상=연장된 아이템)';
COMMENT ON COLUMN fridge_item.expires_at      IS '소비기한 (nullable — 채우기 후 별도 등록)';

-- 기존 데이터 마이그레이션:
-- original_expires_at이 있는 row = 이미 연장된 아이템 → extension_count = 1
UPDATE fridge_item
SET extension_count = 1
WHERE original_expires_at IS NOT NULL;
