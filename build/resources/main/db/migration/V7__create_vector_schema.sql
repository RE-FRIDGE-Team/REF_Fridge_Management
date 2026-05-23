-- ============================================================
-- V7: vector_schema 생성 + pgvector extension 활성화
--
-- 목적:
--   Spring AI VectorStore(pgvector)가 사용할 스키마를 생성한다.
--   food_knowledge 테이블은 Spring AI가 initialize-schema: true 설정으로 자동 생성.
--   이 마이그레이션은 스키마와 extension만 준비한다.
--
-- 구성:
--   vector_schema : Spring AI pgvector 테이블 (food_knowledge) 전용 스키마
--   vector extension: pgvector/pgvector:pg16 이미지에 이미 설치되어 있음
--                     CREATE EXTENSION IF NOT EXISTS로 안전하게 활성화
--
-- Spring AI 설정 (application.yml):
--   spring.ai.vectorstore.pgvector:
--     schema-name: vector_schema
--     table-name: food_knowledge
--     dimensions: 1024          # bge-m3 출력 차원
--     initialize-schema: true   # food_knowledge 테이블 자동 생성
--
-- 작성: 승훈 / 2026-04-26
-- ============================================================

CREATE SCHEMA IF NOT EXISTS vector_schema;

-- pgvector extension: pgvector/pgvector:pg16 이미지에 pre-installed
-- docker/init-schemas.sh에서도 활성화하지만, Flyway에서도 명시적으로 실행
CREATE EXTENSION IF NOT EXISTS vector;
