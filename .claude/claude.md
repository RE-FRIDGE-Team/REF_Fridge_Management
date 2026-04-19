# CLAUDE.md — fridge_server

## 프로젝트 개요
RE:FRIDGE 냉장고 관리 서버. core_server(인식/식품DB)와 독립된 별도 마이크로서비스.

**스택:** Java 21 · Spring Boot 4.0.5 · JPA · QueryDSL 5.1.0 (jakarta) · Redis 7 · MySQL 8.4 · pgvector · Spring AI 1.0

## 포트 현황 (충돌 방지 필수 확인)

| 서비스 | core_server | fridge_server |
|--------|------------|---------------|
| 앱 서버 | 8080 | **8081** |
| MySQL | 3306 | **3307** |
| Redis | 6379 | **6380** |
| PostgreSQL | — | **5433** |

## 아키텍처 원칙

**스타일:** Layered + "경계만 Port/Adapter" (헥사고날 절충)
- 도메인 내부: 기존 core_server 스타일 (AR = `@Entity` 직접)
- 외부 경계만 Port 인터페이스 + Adapter로 격리

**BC 경계:**
- `com.refridge.fridge.*` — Fridge BC (냉장고/아이템 관리)
- `com.refridge.saving.*` — Saving/Statistics BC (같은 JAR, 패키지만 분리)
- notification_server는 별도 서버, Redis Stream/Pub/Sub으로만 통신

**핵심 규칙:**
1. BC 간 직접 의존 금지 → Spring Event 또는 Redis Stream으로만 통신
2. Fridge AR만 Aggregate Root. FridgeSection, FridgeItem은 AR 내부 Entity
3. Outbox 패턴 필수 (core_server 이벤트 수신 시 유실 방지)
4. 도메인 이벤트는 record 타입으로 불변 선언

## 패키지 구조

```
com.refridge.fridge
├── domain/          ← AR, Entity, VO, Event, Policy (순수 도메인, 의존성 없음)
├── application/     ← UseCase 단위로 디렉터리 분리 (fill/, consume/, ...)
│   └── port/        ← 외부 경계 인터페이스
├── adapter/
│   ├── in/web/      ← REST Controller
│   ├── in/event/    ← Redis Stream Consumer
│   └── out/         ← persistence/, coreserver/, ai/, messaging/
└── infrastructure/  ← Config, Scheduler
```

## 네이밍 컨벤션

- AR: `Fridge` (복수 X)
- UseCase 인터페이스: `ConsumeItemUseCase`
- Command: `ConsumeItemCommand` (record)
- Service: `ConsumeItemService implements ConsumeItemUseCase`
- Port (아웃바운드): `GroceryItemCatalogPort` (Port 접미사)
- Adapter (아웃바운드): `CoreServerGroceryItemAdapter implements GroceryItemCatalogPort`
- Controller: `FridgeController`, `FridgeItemController`
- 이벤트: `FridgeItemConsumedEvent` (record, 과거형)

## 테스트 전략

- **단위 테스트:** Spock (Groovy) — `src/test/groovy/**/*Spec.groovy`
    - 도메인 로직, 상태 머신, 불변식 위반 케이스 필수 커버
    - Object Mother 픽스처: `FridgeFixture.groovy`, `FridgeItemFixture.groovy`
- **통합 테스트:** JUnit 5 + Testcontainers — `*IntegrationTest.java`
- **금지:** `@SpringBootTest` 남발 — 슬라이스 테스트(`@DataJpaTest`, `@WebMvcTest`) 우선

## 주요 제약 사항

- Flyway 마이그레이션 파일(`V*__*.sql`)은 **절대 수정 금지** (append-only)
- `@Transactional`은 application 레이어에만 붙임 (domain/adapter에는 원칙적으로 X)
- QueryDSL Q클래스는 `build/generated/querydsl/`에 생성됨 (Git ignore)
- Redis Stream consumer group: `fridge-fill-group`
- `application.yml` 시크릿 값은 환경변수로만 주입 (`${ENV_VAR:default}`)

## Skills 참조

상세 구현 가이드는 `.claude/skills/` 참조:
- `ddd-aggregate.md` — AR/Entity/VO 설계 규칙
- `redis-stream.md` — Redis Stream consumer 구현 패턴
- `spring-ai-rag.md` — ShelfLifeAdvisor RAG 구현
- `outbox-pattern.md` — Outbox + @Scheduled 패턴 (core_server와 동일)