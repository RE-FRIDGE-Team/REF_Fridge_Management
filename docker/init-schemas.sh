#!/bin/bash
set -e

# fridge_server PostgreSQL 초기화 스크립트
# Docker Compose 첫 기동 시 자동 실행
# SQL 파일보다 .sh 스크립트가 docker-entrypoint-initdb.d에서 더 안정적으로 실행됨

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

    -- pgvector 익스텐션 활성화 (Spring AI VectorStore 필수)
    CREATE EXTENSION IF NOT EXISTS vector;

    -- hstore 익스텐션 (pgvector 메타데이터 필터링 지원)
    CREATE EXTENSION IF NOT EXISTS hstore;

    -- uuid-ossp 익스텐션 (uuid_generate_v4() 함수 제공)
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    -- 도메인 스키마 (냉장고 관련 테이블)
    CREATE SCHEMA IF NOT EXISTS fridge_schema;

    -- 벡터 스키마 (Spring AI pgvector 테이블: ShelfLifeAdvisor RAG)
    CREATE SCHEMA IF NOT EXISTS vector_schema;

    -- search_path 기본값 설정
    ALTER DATABASE fridge_db SET search_path TO fridge_schema, vector_schema, public;

EOSQL

echo "✅ fridge_db 초기화 완료: vector, hstore, uuid-ossp 익스텐션 및 스키마 생성됨"