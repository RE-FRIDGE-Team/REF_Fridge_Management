package com.refridge.fridge_management.fridge.infrastructure.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.refridge.fridge_management.fridge.domain.event.FridgeDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fridge BC Outbox Appender.
 *
 * <h2>역할</h2>
 * 도메인 이벤트가 {@code AbstractAggregateRoot.registerEvent()}로 등록되면
 * Spring Data가 {@code FridgeRepository.save()} 직후
 * {@code ApplicationEventPublisher}로 자동 발행한다.
 * 이 컴포넌트는 {@code BEFORE_COMMIT} 단계에서 이벤트를 가로채
 * {@code fridge_pending_event} 테이블에 INSERT한다.
 *
 * <h2>BEFORE_COMMIT을 사용하는 이유</h2>
 * AFTER_COMMIT 이후 INSERT 실패 시 이벤트가 영구 유실된다.
 * BEFORE_COMMIT은 동일 트랜잭션 안이므로 원자성이 보장된다.
 *
 * <h2>Outbox 제외 이벤트</h2>
 * {@link FridgeDomainEvent.FridgeItemExpirationRegisteredEvent}는
 * Redis Stream 발행 대상이 아니므로 저장하지 않는다.
 *
 * <h2>Jackson 3 변경점</h2>
 * Jackson 3에서 직렬화 예외는 checked {@code JsonProcessingException} 대신
 * unchecked {@code JacksonException}으로 변경되었다.
 * {@code throws} 선언 없이 catch만 하면 된다.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FridgeOutboxAppender {

    private final FridgePendingEventRepository repository;
    private final FridgeDomainEventStreamKeyResolver keyResolver;
    private final ObjectMapper objectMapper;   // tools.jackson.databind.ObjectMapper

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFridgeEvent(FridgeDomainEvent event) {
        // ⚠️ FridgeItemExpirationRegisteredEvent는 Outbox 발행 제외
        if (!keyResolver.isOutboxTarget(event)) {
            log.debug("[FridgeOutboxAppender] Outbox 제외 이벤트 skip: {}",
                    event.getClass().getSimpleName());
            return;
        }

        try {
            String payload     = objectMapper.writeValueAsString(event);
            String streamKey   = keyResolver.resolveStreamKey(event);
            String aggregateId = keyResolver.resolveAggregateId(event);

            repository.save(FridgePendingEvent.of(
                    event.eventId(),
                    aggregateId,
                    event.getClass().getSimpleName(),
                    streamKey,
                    payload
            ));

            log.debug("[FridgeOutboxAppender] Outbox INSERT 완료. eventType={}, streamKey={}",
                    event.getClass().getSimpleName(), streamKey);

        } catch (JacksonException e) {
            // Jackson 3: unchecked JacksonException
            log.error("[FridgeOutboxAppender] 이벤트 직렬화 실패. eventType={}, eventId={}",
                    event.getClass().getSimpleName(), event.eventId(), e);
            throw new OutboxSerializationException(
                    "Outbox 이벤트 직렬화 실패: " + event.getClass().getSimpleName(), e);
        } catch (Exception e) {
            log.error("[FridgeOutboxAppender] Outbox INSERT 실패. eventType={}, eventId={}",
                    event.getClass().getSimpleName(), event.eventId(), e);
            throw e;
        }
    }

    public static class OutboxSerializationException extends RuntimeException {
        public OutboxSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}