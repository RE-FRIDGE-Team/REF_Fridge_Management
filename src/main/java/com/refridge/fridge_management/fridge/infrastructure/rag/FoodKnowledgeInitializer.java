package com.refridge.fridge_management.fridge.infrastructure.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 식품 보관 지식 RAG 문서 초기화.
 *
 * <h2>역할</h2>
 * 앱 기동 시 {@code resources/rag/food-storage-knowledge.txt}를 읽어
 * 항목별로 파싱한 뒤 {@link VectorStore}(pgvector)에 임베딩하여 적재한다.
 *
 * <h2>중복 적재 방지</h2>
 * 기동 시 VectorStore에서 시범 쿼리를 실행해 문서가 이미 존재하면 skip한다.
 * PostgreSQL 볼륨이 유지되는 한 재기동마다 재적재하지 않는다.
 *
 * <h2>문서 파싱 전략</h2>
 * {@code food-storage-knowledge.txt}는 {@code [항목명]} 헤더로 시작하는 블록 구조.
 * 각 블록을 하나의 {@link Document}로 변환하고,
 * 메타데이터에 카테고리·보관 구역 태그를 추가해 검색 필터링에 활용한다.
 *
 * <h2>@Order(20)</h2>
 * Bootstrap initializer들과 순서를 맞춰 VectorStore 준비 후 실행.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class FoodKnowledgeInitializer implements ApplicationRunner {

    private static final String KNOWLEDGE_FILE = "rag/food-storage-knowledge.txt";
    private static final String EXISTENCE_CHECK_QUERY = "소고기 냉장 보관";
    private static final int MIN_DOCS_TO_SKIP = 5;

    private final VectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (isAlreadyLoaded()) {
            log.info("[FoodKnowledgeInitializer] VectorStore에 식품 지식 문서 이미 존재 — skip");
            return;
        }

        log.info("[FoodKnowledgeInitializer] 식품 보관 지식 문서 적재 시작...");

        List<Document> documents = loadAndParse();
        vectorStore.add(documents);

        log.info("[FoodKnowledgeInitializer] 식품 보관 지식 {}건 적재 완료", documents.size());
    }

    /**
     * 기존 문서 존재 여부 확인 (중복 적재 방지).
     * 시범 쿼리로 문서가 5건 이상 검색되면 이미 적재된 것으로 판단.
     */
    private boolean isAlreadyLoaded() {
        try {
            List<Document> existing = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(EXISTENCE_CHECK_QUERY)
                            .topK(MIN_DOCS_TO_SKIP)
                            .build()
            );
            return existing.size() >= MIN_DOCS_TO_SKIP;
        } catch (Exception e) {
            log.warn("[FoodKnowledgeInitializer] VectorStore 존재 확인 실패, 재적재 진행: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 텍스트 파일을 [항목명] 블록 단위로 파싱하여 Document 목록으로 변환.
     *
     * <h3>파싱 규칙</h3>
     * - {@code [항목명]} 으로 시작하는 줄 = 새 블록 시작
     * - {@code ====} 구분선과 {@code #} 섹션 헤더는 skip
     * - 각 블록의 텍스트를 Document content로 구성
     * - 블록 내 "카테고리: xxx" 줄에서 카테고리 메타데이터 추출
     */
    private List<Document> loadAndParse() throws Exception {
        ClassPathResource resource = new ClassPathResource(KNOWLEDGE_FILE);

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }

        List<Document> documents = new ArrayList<>();
        StringBuilder currentBlock = new StringBuilder();
        String currentTitle = null;
        String currentCategory = "GENERAL";

        for (String line : lines) {
            // 섹션 구분선·빈 줄·#헤더 처리
            if (line.startsWith("====") || line.startsWith("# ")) {
                continue;
            }

            // 새 블록 시작 ([항목명])
            if (line.startsWith("[") && line.contains("]") && !line.startsWith("[출처")) {
                // 이전 블록 저장
                if (currentTitle != null && currentBlock.length() > 0) {
                    documents.add(buildDocument(currentTitle, currentBlock.toString(), currentCategory));
                }
                currentTitle = line.trim();
                currentBlock = new StringBuilder(line).append("\n");
                currentCategory = "GENERAL";
                continue;
            }

            // 카테고리 메타데이터 추출
            if (line.startsWith("카테고리:") && currentTitle != null) {
                currentCategory = line.replace("카테고리:", "").trim().toUpperCase();
            }

            if (currentTitle != null) {
                currentBlock.append(line).append("\n");
            }
        }

        // 마지막 블록 저장
        if (currentTitle != null && !currentBlock.isEmpty()) {
            documents.add(buildDocument(currentTitle, currentBlock.toString(), currentCategory));
        }

        log.debug("[FoodKnowledgeInitializer] 파싱 완료: {}건", documents.size());
        return documents;
    }

    private Document buildDocument(String title, String content, String category) {
        // 보관 구역 추출 (메타데이터 필터링용)
        String section = "UNKNOWN";
        if (title.contains("냉동")) section = "FREEZER";
        else if (title.contains("냉장")) section = "REFRIGERATED";
        else if (title.contains("상온")) section = "ROOM_TEMPERATURE";
        else if (title.contains("밀프랩")) section = "MEAL_PREP";

        return new Document(
                content.trim(),
                Map.of(
                        "title",    title,
                        "category", category,
                        "section",  section,
                        "source",   "식약처/USDA"
                )
        );
    }
}