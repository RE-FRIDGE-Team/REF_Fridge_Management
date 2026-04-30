package com.refridge.fridge_management.fridge.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Stream XADD 래퍼.
 *
 * <h2>역할</h2>
 * {@link FridgeOutboxRelayer}로부터 이벤트 페이로드를 받아
 * Redis Stream에 {@code XADD}로 발행한다.
 *
 * <h2>메시지 구조</h2>
 * Stream entry는 {@code payload} 필드 하나로 구성된다:
 * <pre>
 * XADD fridge:consumed * payload "{...eventJson...}"
 * </pre>
 *
 * <h2>consumer group 생성</h2>
 * Stream 생성 및 consumer group 초기화는 {@code RedisStreamConfig}에서 담당.
 *
 * @author 승훈
 * @since 2026-04-29
 * @see FridgeOutboxRelayer
 * @see RedisStreamConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FridgeStreamPublisher {

    private final StringRedisTemplate redis;

    private static final String PAYLOAD_FIELD = "payload";

    /**
     * Redis Stream에 이벤트 발행 (XADD).
     *
     * @param streamKey  Redis Stream key (ex: {@code "fridge:consumed"})
     * @param payload    이벤트 JSON 문자열
     * @return Redis가 발급한 Stream entry ID
     * @throws RuntimeException Redis 연결 실패 또는 XADD 실패 시
     */
    public RecordId publish(String streamKey, String payload) {
        StringRecord record = StreamRecords.string(Map.of(PAYLOAD_FIELD, payload))
                .withStreamKey(streamKey);

        RecordId recordId = redis.opsForStream().add(record);

        log.debug("[FridgeStreamPublisher] XADD 완료. streamKey={}, recordId={}",
                streamKey, recordId);

        return recordId;
    }
}