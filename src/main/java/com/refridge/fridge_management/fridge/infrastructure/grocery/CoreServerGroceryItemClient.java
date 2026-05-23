package com.refridge.fridge_management.fridge.infrastructure.grocery;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * core_server GroceryItem 조회 HTTP 클라이언트.
 *
 * <h2>Spring Boot 4 @HttpExchange</h2>
 * Spring Boot 4에서 {@code RestClient} 기반 {@code @HttpExchange}를 사용한다.
 * {@link com.refridge.fridge_management.config.GroceryItemClientConfig}에서 {@code HttpServiceProxyFactory}로 빈 등록.
 *
 * <h2>현재 상태</h2>
 * core_server의 {@code GET /grocery-items/{id}} API가 미완성이므로
 * {@link HttpGroceryItemAdapter}에서 예외를 잡아 {@code Optional.empty()}로 처리.
 *
 * @author 승훈
 * @since 2026-04-26
 */
@HttpExchange("/grocery-items")
public interface CoreServerGroceryItemClient {

    @GetExchange("/{id}")
    CoreServerGroceryItemResponse findById(@PathVariable("id") String id);
}