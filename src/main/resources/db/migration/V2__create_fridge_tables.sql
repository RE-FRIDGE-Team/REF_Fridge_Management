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
-- JPA 매핑 대응:
--   Fridge           → fridge
--   FridgeSection    → fridge_section
--   FridgeItem       → fridge_item
--   FridgeMeta(VO)   → fridge.total_value, active_item_count (Embedded)
--   GroceryItemRef(VO) → fridge_item.grocery_item_* (Embedded 스냅샷)
--   Quantity(VO)     → fridge_item.quantity_amount, quantity_unit (Embedded)
--   Money(VO)        → fridge_item.purchase_price (Embedded)
--   ExpirationInfo(VO) → fridge_item.manufactured_at, expires_at 등 (Embedded)
--
-- 작성: 승훈 / 2025-06-01
-- ============================================================

USE fridge_schema;

-- ──────────────────────────────────────────────────────────────
-- 1. fridge (Aggregate Root)
-- ──────────────────────────────────────────────────────────────
-- ▸ fridgeId     : UUID, 앱에서 직접 생성 (IDENTITY 전략 미사용 — DDD 원칙)
-- ▸ memberId     : 회원 식별자 (유저당 1개 냉장고 → UNIQUE 제약)
-- ▸ total_value  : FridgeMeta.totalValue — ACTIVE 아이템 구매가 총합 (원, 소수 없음)
-- ▸ active_item_count : FridgeMeta.activeItemCount — ACTIVE 아이템 수
-- ▸ version      : JPA @Version 낙관적 락 — 동시 consume/dispose 충돌 감지
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge (
    fridge_id           VARCHAR(36)     NOT NULL    COMMENT 'UUID — Fridge 식별자',
    member_id           VARCHAR(36)     NOT NULL    COMMENT '회원 식별자 (유저당 1개)',
    total_value         DECIMAL(15, 0)  NOT NULL DEFAULT 0
                                                    COMMENT 'FridgeMeta.totalValue — ACTIVE 아이템 구매가 총합(원)',
    active_item_count   INT             NOT NULL DEFAULT 0
                                                    COMMENT 'FridgeMeta.activeItemCount — ACTIVE 아이템 수',
    version             BIGINT          NOT NULL DEFAULT 0
                                                    COMMENT 'JPA 낙관적 락(@Version)',

    CONSTRAINT pk_fridge            PRIMARY KEY (fridge_id),
    CONSTRAINT uq_fridge_member_id  UNIQUE      (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Fridge Aggregate Root — 냉장고';

-- ──────────────────────────────────────────────────────────────
-- 2. fridge_section (구역 Entity)
-- ──────────────────────────────────────────────────────────────
-- ▸ fridgeSectionId  : fridgeId + ":" + sectionType.name() 복합 의미 키
--   (예: "abc-123:REFRIGERATED")
--   구역은 냉장고당 항상 3개 고정이므로 UUID 불필요.
-- ▸ section_type     : ROOM_TEMPERATURE | REFRIGERATED | FREEZER
-- ▸ fridge_id FK     : ON DELETE CASCADE — 냉장고 삭제 시 구역도 함께 삭제
--
-- JPA 매핑:
--   FridgeSection.fridge → @ManyToOne LAZY
--   Fridge.sections     → @OneToMany EAGER, @MapKey(name="sectionType")
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge_section (
    fridge_section_id   VARCHAR(80)     NOT NULL    COMMENT 'fridgeId:sectionType 복합 의미 키',
    fridge_id           VARCHAR(36)     NOT NULL    COMMENT 'FK → fridge.fridge_id',
    section_type        VARCHAR(20)     NOT NULL    COMMENT 'SectionType enum: ROOM_TEMPERATURE | REFRIGERATED | FREEZER',

    CONSTRAINT pk_fridge_section    PRIMARY KEY (fridge_section_id),
    CONSTRAINT fk_fs_fridge         FOREIGN KEY (fridge_id)
                                        REFERENCES fridge (fridge_id)
                                        ON DELETE CASCADE
                                        ON UPDATE CASCADE,
    INDEX idx_fs_fridge_id (fridge_id)
                                        COMMENT 'FridgeSection → Fridge 역방향 조회용'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '냉장고 구역 (상온/냉장/냉동) — FridgeSection Entity';

-- ──────────────────────────────────────────────────────────────
-- 3. fridge_item (아이템 Entity)
-- ──────────────────────────────────────────────────────────────
-- ▸ fridge_id        : FK 컬럼만 보관 (연관관계 없음 — @ManyToOne 제거).
--   memberId 역정규화로 Fridge 역참조 불필요.
-- ▸ fridge_section_id : 연관관계 owner (@ManyToOne LAZY).
--   FridgeSection.items는 mappedBy = "fridgeSection".
-- ▸ member_id        : 역정규화 — Saving BC 이벤트 발행 시 JOIN 없이 사용.
-- ▸ status           : ACTIVE | CONSUMED | DISPOSED | PORTIONED_OUT
-- ▸ version          : JPA 낙관적 락 (@Version)
--
-- GroceryItemRef 스냅샷 컬럼 (core_server 변경에 무관한 이력 보존):
--   grocery_item_id, product_id, grocery_item_name, grocery_item_category,
--   grocery_item_default_unit, min_portion_amount, max_portion_amount
--
-- ExpirationInfo 컬럼:
--   manufactured_at, expires_at, shelf_life_extended, original_expires_at
--
-- Quantity 컬럼:
--   quantity_amount, quantity_unit (QuantityUnit enum)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE fridge_item (
    -- ── 식별자 ────────────────────────────────────────────────
    fridge_item_id          VARCHAR(36)     NOT NULL    COMMENT 'UUID — 아이템 식별자',
    fridge_id               VARCHAR(36)     NOT NULL    COMMENT 'FK → fridge (연관관계 없음, FK 컬럼만)',
    fridge_section_id       VARCHAR(80)     NOT NULL    COMMENT 'FK → fridge_section (연관관계 owner)',
    member_id               VARCHAR(36)     NOT NULL    COMMENT '회원 식별자 (역정규화 — 이벤트 발행용)',

    -- ── GroceryItemRef 스냅샷 (core_server 변경에 독립) ────────
    grocery_item_id         VARCHAR(36)     NOT NULL    COMMENT 'core_server GroceryItem ID 스냅샷',
    product_id              VARCHAR(36)                 COMMENT 'core_server Product ID 스냅샷 (nullable)',
    grocery_item_name       VARCHAR(100)    NOT NULL    COMMENT '식품명 스냅샷',
    grocery_item_category   VARCHAR(30)     NOT NULL    COMMENT 'FoodCategory enum 스냅샷',
    grocery_item_default_unit VARCHAR(20)               COMMENT '기본 단위 스냅샷 (nullable)',
    min_portion_amount      INT                         COMMENT '최소 소분 단위(g) (nullable)',
    max_portion_amount      INT                         COMMENT '최대 소분 단위(g) (nullable)',

    -- ── Quantity VO ──────────────────────────────────────────
    quantity_amount         DECIMAL(10, 2)  NOT NULL    COMMENT 'Quantity.amount',
    quantity_unit           VARCHAR(10)     NOT NULL    COMMENT 'QuantityUnit enum (G/KG/ML/L/EA/PACK/PIECE/SERVING)',

    -- ── Money VO ─────────────────────────────────────────────
    purchase_price          DECIMAL(15, 0)  NOT NULL DEFAULT 0
                                                        COMMENT 'Money.amount — 구매가(원)',

    -- ── ExpirationInfo VO ────────────────────────────────────
    manufactured_at         DATE                        COMMENT '제조일 (nullable)',
    expires_at              DATE            NOT NULL    COMMENT '유통기한 (임박 알림 인덱스 대상)',
    shelf_life_extended     TINYINT(1)      NOT NULL DEFAULT 0
                                                        COMMENT 'ExpirationInfo.shelfLifeExtended (1회 제한)',
    original_expires_at     DATE                        COMMENT '연장 전 원래 만료일 (nullable)',

    -- ── 상태 및 분류 ─────────────────────────────────────────
    section_type            VARCHAR(20)     NOT NULL    COMMENT 'SectionType enum',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                                        COMMENT 'ItemStatus enum (ACTIVE|CONSUMED|DISPOSED|PORTIONED_OUT)',
    processing_type         VARCHAR(20)     NOT NULL    COMMENT 'ItemProcessingType enum (RAW|COOKED|MEAL_KIT 등)',

    -- ── 소분 추적 ─────────────────────────────────────────────
    parent_fridge_item_id   VARCHAR(36)                 COMMENT '소분 원본 아이템 ID (nullable — 소분 자식만 존재)',

    -- ── 낙관적 락 ─────────────────────────────────────────────
    version                 BIGINT          NOT NULL DEFAULT 0
                                                        COMMENT 'JPA 낙관적 락(@Version)',

    CONSTRAINT pk_fridge_item   PRIMARY KEY (fridge_item_id),
    CONSTRAINT fk_fi_fridge     FOREIGN KEY (fridge_id)
                                    REFERENCES fridge (fridge_id)
                                    ON DELETE CASCADE,
    CONSTRAINT fk_fi_section    FOREIGN KEY (fridge_section_id)
                                    REFERENCES fridge_section (fridge_section_id)
                                    ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '냉장고 아이템 — FridgeItem Entity';
