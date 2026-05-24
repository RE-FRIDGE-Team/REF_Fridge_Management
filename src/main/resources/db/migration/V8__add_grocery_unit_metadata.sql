-- ============================================================
-- V8: fridge_item에 단위 메타데이터 컬럼 3개 추가
--
-- 변경 이유:
--   core_server v3가 단위 메타데이터(allowedUsageUnits, defaultUsageUnit, pieceWeightGram)를
--   제공하기 시작함에 따라, fridge_server도 GroceryItemRef VO에 영구 저장하여
--   매 Cook마다 core_server를 재호출하지 않도록 한다.
--
-- 추가 컬럼:
--   grocery_item_allowed_usage_units_csv VARCHAR(200)
--     - 입력 가능 단위 코드 CSV (예: "EGG,GRAM" 또는 "TABLESPOON,TEASPOON,CUP,MILLILITER")
--     - core_server REFGroceryItemUsageMetadata와 동일 패턴 (별도 테이블 회피)
--
--   grocery_item_default_usage_unit VARCHAR(20)
--     - 첫 진입 시 활성화될 단위 코드 (allowedUsageUnits 중 하나)
--
--   grocery_item_piece_weight_gram INT
--     - 1개당 중량(g). COUNT 계열 단위 ↔ GRAM 환산용
--     - 예: 계란 1알 = 60g, 두부 1모 = 300g
--
-- 기존 데이터:
--   v4 이전에 채워진 fridge_item은 새 컬럼이 NULL.
--   Cook 시점에 UsageSpec이 fallback(RatioUsage) 처리하므로 호환성 유지.
--
-- 작성: 승훈 / 2026-05-15
-- ============================================================

ALTER TABLE fridge_item
    ADD COLUMN grocery_item_allowed_usage_units_csv VARCHAR(200),
    ADD COLUMN grocery_item_default_usage_unit      VARCHAR(20),
    ADD COLUMN grocery_item_piece_weight_gram       INT;

COMMENT ON COLUMN fridge_item.grocery_item_allowed_usage_units_csv
    IS '입력 가능 단위 코드 CSV (core_server REFUsageUnit BASE/INPUT)';
COMMENT ON COLUMN fridge_item.grocery_item_default_usage_unit
    IS '첫 진입 시 활성화될 단위 코드 (allowedUsageUnits 중 하나)';
COMMENT ON COLUMN fridge_item.grocery_item_piece_weight_gram
    IS '1개당 중량(g). COUNT 계열 ↔ GRAM 환산용 (예: 계란 60, 두부 300)';