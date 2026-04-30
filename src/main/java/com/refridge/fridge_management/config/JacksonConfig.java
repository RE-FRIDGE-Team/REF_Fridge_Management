package com.refridge.fridge_management.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 + Spring Boot 4.0.5 설정.
 *
 * <h2>핵심 사항</h2>
 * <ul>
 *   <li>{@code JsonMapperBuilderCustomizer} 패키지:
 *       {@code org.springframework.boot.jackson.autoconfigure} (Spring Boot 4.x)</li>
 *   <li>Jackson 3 기본값으로 이미 제공되는 것:
 *       {@code Instant}/{@code LocalDate} ISO-8601 직렬화,
 *       {@code JavaTimeModule} 자동 등록 — 별도 설정 불필요</li>
 *   <li>Jackson 3에서 {@code ObjectMapper}는 {@code JsonMapper}의 상위 타입이므로
 *       {@code JsonMapper} 빈을 등록하면 {@code ObjectMapper}로도 주입 가능</li>
 * </ul>
 *
 * <h2>등록 방식 — JsonMapperBuilderCustomizer + JsonMapper 빈 동시 등록</h2>
 * {@code JsonMapperBuilderCustomizer}로 Spring Boot 자동 설정을 커스터마이징하고,
 * {@code JsonMapper} 빈을 직접 등록해 {@code @Autowired ObjectMapper} 주입이
 * 정상 동작하도록 보장한다.
 * ({@code FridgeOutboxAppender}, {@code FridgeItemHistoryAppender}에서
 * {@code ObjectMapper}를 주입받으므로 빈 등록 필수)
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Configuration
public class JacksonConfig {

    /**
     * Spring Boot 자동 설정 커스터마이저.
     * Spring MVC HttpMessageConverter 등 Boot 자동 설정과 일관성 유지.
     */
    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                // 알 수 없는 필드 무시 — 이벤트 스키마 변경 시 하위 호환성
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * {@code ObjectMapper} / {@code JsonMapper} 빈 등록.
     *
     * <p>Jackson 3: {@code JsonMapper extends ObjectMapper} 이므로
     * {@code @Autowired ObjectMapper}와 {@code @Autowired JsonMapper} 모두 이 빈이 주입된다.
     *
     * <p>Jackson 3 기본값으로 처리되는 것:
     * <ul>
     *   <li>날짜/시간 ISO-8601 직렬화 ({@code DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS = false})</li>
     *   <li>{@code JavaTimeModule} 자동 등록 ({@code LocalDate}, {@code Instant} 지원)</li>
     * </ul>
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}