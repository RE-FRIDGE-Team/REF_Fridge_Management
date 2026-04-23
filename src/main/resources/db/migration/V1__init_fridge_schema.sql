-- ============================================================
-- V1: fridge_schema(database) 생성
--
-- 목적:
--   fridge_server 전용 MySQL 데이터베이스를 격리 생성한다.
--   MySQL에서 SCHEMA = DATABASE이므로 CREATE SCHEMA와 동일하다.
--   core_server(포트 3306, DB: ref_core)와 완전히 분리된
--   별도 포트(3307)의 별도 DB로 운영한다.
--
-- 실행 시점:
--   앱 기동 시 Flyway가 flyway_schema_history 테이블을 확인하고
--   아직 적용되지 않은 버전을 순서대로 자동 실행한다.
--   이미 적용된 스크립트는 체크섬 검증 후 건너뛴다.
--
-- 주의:
--   한 번 적용된 이 파일은 절대 수정하지 않는다.
--   스키마 변경이 필요하면 V2, V3... 새 파일을 추가한다.
--
-- 작성: 승훈 / 2025-06-01
-- ============================================================

CREATE DATABASE IF NOT EXISTS fridge_schema
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
