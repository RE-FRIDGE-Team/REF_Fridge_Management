-- fridge_schema 생성 (Docker init-schemas.sql에서 이미 했다면 생략 가능)
CREATE SCHEMA IF NOT EXISTS fridge_schema;

-- fridge 테이블
CREATE TABLE fridge_schema.fridge (
                                      fridge_id           VARCHAR(36)    NOT NULL,
                                      member_id           VARCHAR(36)    NOT NULL,
                                      total_value         NUMERIC(19,0)  NOT NULL DEFAULT 0,
                                      active_item_count   INTEGER        NOT NULL DEFAULT 0,
                                      version             BIGINT,
                                      CONSTRAINT pk_fridge PRIMARY KEY (fridge_id),
                                      CONSTRAINT uq_fridge_member_id UNIQUE (member_id)
);

-- fridge_section 테이블
CREATE TABLE fridge_schema.fridge_section (
                                              fridge_section_id   VARCHAR(80)    NOT NULL,
                                              fridge_id           VARCHAR(36)    NOT NULL,
                                              section_type        VARCHAR(20)    NOT NULL,
                                              CONSTRAINT pk_fridge_section PRIMARY KEY (fridge_section_id),
                                              CONSTRAINT fk_fridge_section_fridge FOREIGN KEY (fridge_id)
                                                  REFERENCES fridge_schema.fridge (fridge_id)
);

-- fridge_item 테이블
CREATE TABLE fridge_schema.fridge_item (
                                           fridge_item_id          VARCHAR(36)    NOT NULL,
                                           member_id               VARCHAR(36)    NOT NULL,
                                           fridge_id               VARCHAR(36)    NOT NULL,
                                           fridge_section_id       VARCHAR(80)    NOT NULL,
                                           grocery_item_id         VARCHAR(255),
                                           product_id              VARCHAR(255),
                                           grocery_item_name       VARCHAR(255),
                                           grocery_item_category   VARCHAR(50),
                                           grocery_item_default_unit VARCHAR(20),
                                           min_portion_amount      INTEGER,
                                           max_portion_amount      INTEGER,
                                           quantity_amount         NUMERIC(10,2)  NOT NULL,
                                           quantity_unit           VARCHAR(20)    NOT NULL,
                                           purchase_price          NUMERIC(19,0)  NOT NULL,
                                           manufactured_at         DATE,
                                           expires_at              DATE           NOT NULL,
                                           shelf_life_extended     BOOLEAN        NOT NULL DEFAULT FALSE,
                                           original_expires_at     DATE,
                                           section_type            VARCHAR(20)    NOT NULL,
                                           status                  VARCHAR(20)    NOT NULL,
                                           processing_type         VARCHAR(20)    NOT NULL,
                                           parent_fridge_item_id   VARCHAR(36),
                                           version                 BIGINT,
                                           CONSTRAINT pk_fridge_item PRIMARY KEY (fridge_item_id),
                                           CONSTRAINT fk_fridge_item_fridge FOREIGN KEY (fridge_id)
                                               REFERENCES fridge_schema.fridge (fridge_id),
                                           CONSTRAINT fk_fridge_item_section FOREIGN KEY (fridge_section_id)
                                               REFERENCES fridge_schema.fridge_section (fridge_section_id)
);

CREATE INDEX idx_fi_fridge_status  ON fridge_schema.fridge_item (fridge_id, status);
CREATE INDEX idx_fi_expires_at     ON fridge_schema.fridge_item (expires_at);
CREATE INDEX idx_fi_member_status  ON fridge_schema.fridge_item (member_id, status, expires_at);