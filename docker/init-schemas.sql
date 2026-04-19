-- fridge_server PostgreSQL 초기화 스크립트
-- Docker Compose 첫 기동 시 자동 실행

-- pgvector 익스텐션 활성화 (Spring AI VectorStore 필수)
CREATE EXTENSION IF NOT EXISTS vector;

-- 도메인 스키마 (냉장고 관련 테이블)
CREATE SCHEMA IF NOT EXISTS fridge_schema;

-- 벡터 스키마 (Spring AI pgvector 테이블: ShelfLifeAdvisor RAG)
CREATE SCHEMA IF NOT EXISTS vector_schema;

-- search_path 기본값 설정
ALTER DATABASE fridge_db SET search_path TO fridge_schema, vector_schema, public;
