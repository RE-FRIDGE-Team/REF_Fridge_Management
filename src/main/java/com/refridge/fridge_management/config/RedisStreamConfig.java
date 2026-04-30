package com.refridge.fridge_management.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Redis Stream 초기화 설정.
 *
 * <h2>역할</h2>
 * fridge_server 기동 시 Redis Stream과 Consumer Group을 생성한다.
 * 이미 존재하는 경우 {@code BUSYGROUP} 오류를 무시한다.
 *
 * <h2>Consumer Group 목록</h2>
 * <pre>
 * fridge:consumed      → saving-group
 * fridge:disposed      → saving-group
 * fridge:cooked        → saving-group
 * fridge:moved         → stats-group     (future)
 * fridge:portioned     → stats-group     (future)
 * fridge:extended      → stats-group     (future)
 * fridge:near-expiry   → noti-near-expiry-group
 * fridge:fill-completed→ feedback-fill-group
 * </pre>
 *
 * <h2>ReadOffset.latest()</h2>
 * {@code $}로 설정해 consumer group 생성 이후의 메시지만 읽는다.
 * (기존 적재된 메시지 재처리 방지)
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Configuration
public class RedisStreamConfig {

    private final StringRedisTemplate redis;

    public RedisStreamConfig(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void initStreamsAndGroups() {
        createStreamGroup("fridge:consumed",       "saving-group");
        createStreamGroup("fridge:disposed",       "saving-group");
        createStreamGroup("fridge:cooked",         "saving-group");
        createStreamGroup("fridge:moved",          "stats-group");
        createStreamGroup("fridge:portioned",      "stats-group");
        createStreamGroup("fridge:extended",       "stats-group");
        createStreamGroup("fridge:near-expiry",    "noti-near-expiry-group");
        createStreamGroup("fridge:fill-completed", "feedback-fill-group");
    }

    /**
     * Stream + Consumer Group 생성.
     * Stream이 존재하지 않으면 {@code MKSTREAM} 옵션으로 자동 생성.
     * Consumer Group이 이미 존재하면 {@code BUSYGROUP} 오류를 무시한다.
     */
    private void createStreamGroup(String streamKey, String groupName) {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.latest(), groupName);
            log.info("[RedisStreamConfig] Stream/Group 생성 완료: {} / {}", streamKey, groupName);
        } catch (RedisSystemException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                log.debug("[RedisStreamConfig] 이미 존재하는 Group: {} / {} — skip",
                        streamKey, groupName);
            } else {
                log.error("[RedisStreamConfig] Stream/Group 생성 실패: {} / {}",
                        streamKey, groupName, e);
                throw e;
            }
        }
    }
}