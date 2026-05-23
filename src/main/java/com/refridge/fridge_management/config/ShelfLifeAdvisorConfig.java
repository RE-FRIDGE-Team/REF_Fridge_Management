package com.refridge.fridge_management.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 소비기한 추천 AI ChatClient 빈 등록.
 *
 * <h2>Provider 스위칭 전략</h2>
 * {@code refridge.ai.provider} 설정값에 따라 사용할 ChatModel을 결정한다.
 * Java 코드를 변경하지 않고 {@code application.yml}만 수정하면 provider 교체 가능.
 *
 * <h2>현재 지원 provider</h2>
 * <pre>
 * ollama  → OllamaChatModel (gemma4:e4b, 로컬)          ← 현재 활성
 * gemini  → OpenAiChatModel (Gemini OpenAI 호환 엔드포인트) ← 운영 전환 시
 * openai  → OpenAiChatModel (GPT-4o mini)               ← 운영 전환 시
 * </pre>
 *
 * <h2>운영 전환 절차</h2>
 * <pre>
 * 1. build.gradle: spring-ai-starter-model-openai 추가
 * 2. application.yml: refridge.ai.provider=gemini
 * 3. application.yml: spring.ai.openai.api-key, base-url 설정
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Configuration
public class ShelfLifeAdvisorConfig {

    @Value("${refridge.ai.provider}")
    private String provider;

    /**
     * 소비기한 추천 전용 {@code ChatClient} 빈.
     *
     * <p>qualifier: {@code shelfLifeChatClient}
     * {@link com.refridge.fridge_management.fridge.infrastructure.advisor.LlmShelfLifeAdvisor}에서
     * {@code @Qualifier("shelfLifeChatClient")}로 주입.
     *
     * @param ollamaChatModel Spring AI 자동 설정 Ollama 모델
     */
    @Bean("shelfLifeChatClient")
    public ChatClient shelfLifeChatClient(OllamaChatModel ollamaChatModel) {
        log.info("[ShelfLifeAdvisorConfig] AI provider={}", provider);

        return switch (provider) {
            case "ollama" -> ChatClient.builder(ollamaChatModel).build();

            // ── 운영 전환 시 아래 케이스 추가 ──────────────────────────
            // case "gemini", "openai" -> {
            //     var api = OpenAiApi.builder()
            //             .baseUrl(geminiBaseUrl)    // https://generativelanguage.googleapis.com/v1beta/openai
            //             .apiKey(geminiApiKey)
            //             .build();
            //     var model = OpenAiChatModel.builder()
            //             .openAiApi(api)
            //             .defaultOptions(OpenAiChatOptions.builder()
            //                     .model("gemini-2.0-flash")
            //                     .temperature(0.1)
            //                     .build())
            //             .build();
            //     yield ChatClient.builder(model).build();
            // }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AI provider: " + provider +
                            " (지원 목록: ollama, gemini, openai)");
        };
    }
}