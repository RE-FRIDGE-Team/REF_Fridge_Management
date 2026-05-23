package com.refridge.fridge_management.fridge.infrastructure.advisor;

import com.refridge.fridge_management.fridge.domain.history.FridgeItemContext;
import com.refridge.fridge_management.fridge.domain.history.FridgeItemHistory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LLM 기반 소비기한 연장 추천 어드바이저 (RAG 포함).
 *
 * <h2>추천 흐름</h2>
 * <pre>
 * 1. FridgeItemContext → 검색 쿼리 구성
 * 2. VectorStore.similaritySearch() → 관련 식품 보관 지식 Top K 검색
 * 3. System Prompt(RAG 문서 주입) + User Prompt(FridgeItemContext) 조합
 * 4. ChatClient.call().entity(ShelfLifeRecommendation.class) → Structured Output
 * 5. 유효하지 않은 응답 또는 예외 → fallback(카테고리 기본값)
 * </pre>
 *
 * <h2>FoodCategory enum</h2>
 * GRAIN, VEGETABLE, FRUIT, MEAT, SEAFOOD, DAIRY, EGG,
 * PROCESSED, BEVERAGE, SEASONING, COOKED, ETC
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
public class LlmShelfLifeAdvisor implements ShelfLifeAdvisorPort {

    private final ChatClient    chatClient;
    private final VectorStore   vectorStore;
    private final String        systemPromptTemplate;

    private final int    ragTopK;
    private final double ragSimilarityThreshold;
    private final int    fallbackDays;

    private final Timer               latencyTimer;
    private final Counter             fallbackCounter;
    private final DistributionSummary recommendedDaysSummary;

    public LlmShelfLifeAdvisor(
            @Qualifier("shelfLifeChatClient") ChatClient chatClient,
            VectorStore vectorStore,
            MeterRegistry meterRegistry,
            @Value("${refridge.ai.rag.top-k:3}")                  int    ragTopK,
            @Value("${refridge.ai.rag.similarity-threshold:0.6}") double ragSimilarityThreshold,
            @Value("${refridge.ai.fallback.default-days:3}")       int    fallbackDays
    ) {
        this.chatClient             = chatClient;
        this.vectorStore            = vectorStore;
        this.ragTopK                = ragTopK;
        this.ragSimilarityThreshold = ragSimilarityThreshold;
        this.fallbackDays           = fallbackDays;
        this.systemPromptTemplate   = loadSystemPrompt();

        this.latencyTimer = meterRegistry.timer("refridge.ai.shelf_life.latency");
        this.fallbackCounter = meterRegistry.counter("refridge.ai.shelf_life.fallback");
        this.recommendedDaysSummary = DistributionSummary
                .builder("refridge.ai.shelf_life.recommended_days")
                .register(meterRegistry);
    }

    // ── 메인 ─────────────────────────────────────────────────────────

    @Override
    public ShelfLifeRecommendation recommend(FridgeItemContext context) {
        long start = System.currentTimeMillis();
        try {
            String ragContext   = searchRagContext(context);
            String systemPrompt = systemPromptTemplate.replace("{rag_context}", ragContext);
            String userPrompt   = buildUserPrompt(context);

            log.debug("[LlmShelfLifeAdvisor] LLM 호출. item={}", context.fridgeItemId());

            ShelfLifeRecommendation result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(ShelfLifeRecommendation.class);

            if (result == null || result.recommendedDays() < 0) {
                log.warn("[LlmShelfLifeAdvisor] 유효하지 않은 응답 — fallback: {}", result);
                return fallback(context, "LLM 응답 유효성 검증 실패");
            }

            long elapsed = System.currentTimeMillis() - start;
            latencyTimer.record(elapsed, TimeUnit.MILLISECONDS);
            recommendedDaysSummary.record(result.recommendedDays());

            log.info("[LlmShelfLifeAdvisor] 추천 완료. item={}, days={}, confidence={}, ms={}",
                    context.fridgeItemId(), result.recommendedDays(),
                    result.confidence(), elapsed);
            return result;

        } catch (Exception e) {
            latencyTimer.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
            log.error("[LlmShelfLifeAdvisor] LLM 호출 실패 — fallback: item={}, error={}",
                    context.fridgeItemId(), e.getMessage(), e);
            return fallback(context, e.getMessage());
        }
    }

    // ── RAG ──────────────────────────────────────────────────────────

    private String searchRagContext(FridgeItemContext context) {
        try {
            String query = buildRagQuery(context);
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(ragTopK)
                            .similarityThreshold(ragSimilarityThreshold)
                            .build()
            );
            if (docs.isEmpty()) return "관련 보관 지식 없음 (LLM 자체 지식으로 추천)";

            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.warn("[LlmShelfLifeAdvisor] RAG 검색 실패 — LLM 단독 추론: {}", e.getMessage());
            return "보관 지식 검색 불가 (LLM 자체 지식으로 추천)";
        }
    }

    private String buildRagQuery(FridgeItemContext context) {
        String name      = context.groceryItemRef().getName();
        String sectionKr = toKoreanSection(context.currentSection());
        String openedKr  = context.estimatedOpened() ? "개봉 후" : "미개봉";
        String category  = context.groceryItemRef().getCategory() != null
                ? context.groceryItemRef().getCategory().name() : "";

        return "%s %s %s 보관 소비기한 %s".formatted(name, sectionKr, openedKr, category).trim();
    }

    // ── User Prompt ───────────────────────────────────────────────────

    private String buildUserPrompt(FridgeItemContext context) {
        LocalDate today = LocalDate.now();

        // 소비기한 상태
        String expiryStatus = "소비기한 미설정";
        LocalDate expiresAt = context.expirationInfo().getExpiresAt();
        if (expiresAt != null) {
            long daysUntil = ChronoUnit.DAYS.between(today, expiresAt);
            expiryStatus = daysUntil >= 0
                    ? daysUntil + "일 남음 (" + expiresAt + ")"
                    : Math.abs(daysUntil) + "일 초과 (" + expiresAt + ")";
        }

        // 보관 기간 (addedAt은 LocalDate)
        long storedDays = context.addedAt() != null
                ? ChronoUnit.DAYS.between(context.addedAt(), today) : 0;

        // 연장 횟수
        int extensionCount = context.expirationInfo().getExtensionCount();

        // 이력 포맷
        String historyText = formatHistories(context.historyEntries());

        return """
                [아이템 정보]
                식재료명: %s
                카테고리: %s
                가공 유형: %s
                현재 소비기한: %s
                현재 보관 구역: %s
                냉장고 추가일: %s (총 %d일 보관)
                누적 연장 횟수: %d회
                개봉 추정: %s

                [보관 이력] (%d건)
                %s
                """.formatted(
                context.groceryItemRef().getName(),
                context.groceryItemRef().getCategory() != null
                        ? context.groceryItemRef().getCategory().getDisplayName() : "알 수 없음",
                context.processingType(),
                expiryStatus,
                toKoreanSection(context.currentSection()),
                context.addedAt() != null ? context.addedAt() : "알 수 없음",
                storedDays,
                extensionCount,
                context.estimatedOpened() ? "예 (이동·소분·요리 이력 있음)" : "아니오",
                context.historyEntries().size(),
                historyText.isEmpty() ? "이력 없음" : historyText
        );
    }

    private String formatHistories(List<FridgeItemHistory> histories) {
        if (histories == null || histories.isEmpty()) return "";
        return histories.stream()
                .map(h -> "- %s: %s%s".formatted(
                        h.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        toKoreanEventType(h.getEventType()),
                        formatPayload(h)
                ))
                .collect(Collectors.joining("\n"));
    }

    private String toKoreanEventType(FridgeItemHistory.HistoryEventType type) {
        return switch (type) {
            case ADDED          -> "냉장고 추가";
            case MOVED          -> "구역 이동";
            case PORTIONED      -> "소분";
            case COOKED         -> "요리 재료 사용";
            case SHELF_EXTENDED -> "소비기한 연장";
        };
    }

    private String formatPayload(FridgeItemHistory h) {
        if (h.getPayload() == null) return "";
        try {
            return switch (h.getEventType()) {
                case MOVED -> {
                    String from = h.getPayload().path("from").asText("");
                    String to   = h.getPayload().path("to").asText("");
                    yield " (%s → %s)".formatted(toKoreanSection(from), toKoreanSection(to));
                }
                case PORTIONED -> {
                    int count = h.getPayload().path("portionCount").asInt(0);
                    yield count > 0 ? " (%d등분)".formatted(count) : "";
                }
                case SHELF_EXTENDED -> {
                    int days = h.getPayload().path("additionalDays").asInt(0);
                    yield days > 0 ? " (+%d일)".formatted(days) : "";
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private String toKoreanSection(Object section) {
        if (section == null) return "알 수 없음";
        return switch (section.toString()) {
            case "FREEZER"          -> "냉동";
            case "REFRIGERATED"     -> "냉장";
            case "ROOM_TEMPERATURE" -> "상온";
            default                 -> section.toString();
        };
    }

    // ── Fallback ─────────────────────────────────────────────────────

    private ShelfLifeRecommendation fallback(FridgeItemContext context, String reason) {
        fallbackCounter.increment();
        int days = estimateFallbackDays(context);
        log.warn("[LlmShelfLifeAdvisor] Fallback: days={}, reason={}", days, reason);
        return new ShelfLifeRecommendation(
                days,
                0.3,
                "AI 추천 서비스에 일시적인 문제가 발생하여 기본값을 제공합니다. " +
                        "단, 이는 AI 추천이며 실제 보관 상태에 따라 차이가 있을 수 있습니다. " +
                        "반드시 색깔·냄새·질감 등 상태를 직접 확인하신 후 섭취하시기 바랍니다."
        );
    }

    private int estimateFallbackDays(FridgeItemContext context) {
        if (context.groceryItemRef().getCategory() == null) return fallbackDays;
        boolean opened = context.estimatedOpened();
        return switch (context.groceryItemRef().getCategory()) {
            case GRAIN     -> opened ? 30 : 180;
            case VEGETABLE, FRUIT -> opened ? 2  : 5;
            case MEAT      -> opened ? 1  : 3;
            case SEAFOOD   -> opened ? 1  : 2;
            case DAIRY     -> opened ? 2  : 5;
            case EGG       -> opened ? 3  : 21;
            case PROCESSED -> opened ? 7  : 30;
            case COOKED    -> opened ? 2  : 4;
            case BEVERAGE  -> opened ? 3  : 14;
            case SEASONING -> opened ? 60 : 365;
            case ETC       -> fallbackDays;
        };
    }

    // ── System Prompt 로드 ────────────────────────────────────────────

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/shelf-life-system.st");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "prompts/shelf-life-system.st 파일을 찾을 수 없습니다.", e);
        }
    }
}