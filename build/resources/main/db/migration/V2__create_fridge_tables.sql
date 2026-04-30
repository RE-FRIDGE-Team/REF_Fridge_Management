-- ============================================================
-- V2: fridge, fridge_section, fridge_item 테이블 생성
--
-- 목적:
--   Fridge Aggregate Root 및 하위 Entity(FridgeSection, FridgeItem)의
--   물리 테이블을 생성한다. JPA @Entity 매핑과 1:1 대응.
--
-- 테이블 생성 순서 (FK 의존성):
--   1. fridge          (독립)
--   2. fridge_section  (fridge_id FK → fridge)
--   3. fridge_item     (fridge_id FK → fridge,
--                       fridge_section_id FK → fridge_section)
--
-- PostgreSQL 주의사항:
--   - MySQL의 ENGINE=InnoDB, CHARSET 옵션 없음
--   - TINYINT(1) → BOOLEAN
--   - DECIMAL → NUMERIC
--   - BIGINT AUTO_INCREMENT → BIGINT (JPA @Version은 앱에서 관리)
--   - COMMENT ON TABLE/COLUMN 구문 사용
--
-- 작성: 승훈 / 2025-06-01
-- ============================================================

SET search_path TO fridge_schema;

-- ──────────────────────────────────────────────────────────────
-- 1. fridge (Aggregate Root)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge (
                        fridge_id           VARCHAR(36)     NOT NULL,
                        member_id           VARCHAR(36)     NOT NULL,
                        total_value         NUMERIC(15, 0)  NOT NULL DEFAULT 0,
                        active_item_count   INT             NOT NULL DEFAULT 0,
                        version             BIGINT          NOT NULL DEFAULT 0,

                        CONSTRAINT pk_fridge           PRIMARY KEY (fridge_id),
                        CONSTRAINT uq_fridge_member_id UNIQUE      (member_id)
);

COMMENT ON TABLE  fridge                    IS 'Fridge Aggregate Root — 냉장고';
COMMENT ON COLUMN fridge.fridge_id          IS 'UUID — Fridge 식별자';
COMMENT ON COLUMN fridge.member_id          IS '회원 식별자 (유저당 1개)';
COMMENT ON COLUMN fridge.total_value        IS 'FridgeMeta.totalValue — ACTIVE 아이템 구매가 총합(원)';
COMMENT ON COLUMN fridge.active_item_count  IS 'FridgeMeta.activeItemCount — ACTIVE 아이템 수';
COMMENT ON COLUMN fridge.version            IS 'JPA 낙관적 락(@Version)';

-- ──────────────────────────────────────────────────────────────
-- 2. fridge_section (구역 Entity)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge_section (
                                fridge_section_id   VARCHAR(80)     NOT NULL,
                                fridge_id           VARCHAR(36)     NOT NULL,
                                section_type        VARCHAR(20)     NOT NULL,

                                CONSTRAINT pk_fridge_section PRIMARY KEY (fridge_section_id),
                                CONSTRAINT fk_fs_fridge      FOREIGN KEY (fridge_id)
                                    REFERENCES fridge (fridge_id)
                                    ON DELETE CASCADE
                                    ON UPDATE CASCADE
);

COMMENT ON TABLE  fridge_section                  IS '냉장고 구역 (상온/냉장/냉동) — FridgeSection Entity';
COMMENT ON COLUMN fridge_section.fridge_section_id IS 'fridgeId:sectionType 복합 의미 키';
COMMENT ON COLUMN fridge_section.fridge_id         IS 'FK → fridge.fridge_id';
COMMENT ON COLUMN fridge_section.section_type      IS 'SectionType enum: ROOM_TEMPERATURE | REFRIGERATED | FREEZER';

CREATE INDEX idx_fs_fridge_id ON fridge_section (fridge_id);

-- ──────────────────────────────────────────────────────────────
-- 3. fridge_item (아이템 Entity)
-- ──────────────────────────────────────────────────────────────
-- V5에서 변경 예정:
--   shelf_life_extended → extension_count (INT)
--   expires_at: NOT NULL → NULL 허용
--   manufactured_at: 컬럼 제거
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge_item (
    -- 식별자
                             fridge_item_id          VARCHAR(36)     NOT NULL,
                             fridge_id               VARCHAR(36)     NOT NULL,
                             fridge_section_id       VARCHAR(80)     NOT NULL,
                             member_id               VARCHAR(36)     NOT NULL,

    -- GroceryItemRef 스냅샷
                             grocery_item_id         VARCHAR(36)     NOT NULL,
                             product_id              VARCHAR(36),
                             grocery_item_name       VARCHAR(100)    NOT NULL,
                             grocery_item_category   VARCHAR(30)     NOT NULL,
                             grocery_item_default_unit VARCHAR(20),
                             min_portion_amount      INT,
                             max_portion_amount      INT,

    -- Quantity VO
                             quantity_amount         NUMERIC(10, 2)  NOT NULL,
                             quantity_unit           VARCHAR(10)     NOT NULL,

    -- Money VO
                             purchase_price          NUMERIC(15, 0)  NOT NULL DEFAULT 0,

    -- ExpirationInfo VO (V5에서 일부 변경)
                             manufactured_at         DATE,
                             expires_at              DATE            NOT NULL,
                             shelf_life_extended     BOOLEAN         NOT NULL DEFAULT FALSE,
                             original_expires_at     DATE,

    -- 상태 및 분류
                             section_type            VARCHAR(20)     NOT NULL,
                             status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
                             processing_type         VARCHAR(20)     NOT NULL,

    -- 소분 추적
                             parent_fridge_item_id   VARCHAR(36),

    -- 낙관적 락
                             version                 BIGINT          NOT NULL DEFAULT 0,

                             CONSTRAINT pk_fridge_item   PRIMARY KEY (fridge_item_id),
                             CONSTRAINT fk_fi_fridge     FOREIGN KEY (fridge_id)
                                 REFERENCES fridge (fridge_id)
                                 ON DELETE CASCADE,
                             CONSTRAINT fk_fi_section    FOREIGN KEY (fridge_section_id)
                                 REFERENCES fridge_section (fridge_section_id)
                                 ON DELETE CASCADE
);

COMMENT ON TABLE  fridge_item                        IS '냉장고 아이템 — FridgeItem Entity';
COMMENT ON COLUMN fridge_item.fridge_item_id         IS 'UUID — 아이템 식별자';
COMMENT ON COLUMN fridge_item.fridge_id              IS 'FK → fridge (연관관계 없음, FK 컬럼만)';
COMMENT ON COLUMN fridge_item.fridge_section_id      IS 'FK → fridge_section (연관관계 owner)';
COMMENT ON COLUMN fridge_item.member_id              IS '회원 식별자 (역정규화 — 이벤트 발행용)';
COMMENT ON COLUMN fridge_item.grocery_item_id        IS 'core_server GroceryItem ID 스냅샷';
COMMENT ON COLUMN fridge_item.quantity_unit          IS 'QuantityUnit enum (G/KG/ML/L/EA/PACK/PIECE/SERVING)';
COMMENT ON COLUMN fridge_item.purchase_price         IS 'Money.amount — 구매가(원)';
COMMENT ON COLUMN fridge_item.expires_at             IS '소비기한 (V5에서 nullable로 변경 예정)';
COMMENT ON COLUMN fridge_item.shelf_life_extended    IS 'V5에서 extension_count(INT)로 대체 예정';
COMMENT ON COLUMN fridge_item.status                 IS 'ItemStatus enum (ACTIVE|CONSUMED|DISPOSED|PORTIONED_OUT)';
COMMENT ON COLUMN fridge_item.processing_type        IS 'ItemProcessingType enum (RAW|COOKED|MEAL_KIT 등)';
COMMENT ON COLUMN fridge_item.parent_fridge_item_id  IS '소분 원본 아이템 ID (nullable — 소분 자식만 존재)';
COMMENT ON COLUMN fridge_item.version                IS 'JPA 낙관적 락(@Version)';