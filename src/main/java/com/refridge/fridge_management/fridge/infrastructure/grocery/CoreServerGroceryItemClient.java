package com.refridge.fridge_management.fridge.infrastructure.grocery;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * core_server GroceryItem 조회 HTTP 클라이언트.
 *
 * <h2>v4 변경점</h2>
 * core_server v3가 단위 메타데이터 전용 엔드포인트
 * {@code GET /grocery-items/{id}/metadata}를 노출함에 따라 경로 변경.
 *
 * <h2>Spring Boot 4 @HttpExchange</h2>
 * Spring Boot 4에서 {@code RestClient} 기반 {@code @HttpExchange}를 사용한다.
 * {@link com.refridge.fridge_management.config.GroceryItemClientConfig}에서
 * {@code HttpServiceProxyFactory}로 빈 등록.
 *
 * <h2>호출 흐름</h2>
 * <pre>
 *   FillFridgeUseCase
 *     → GroceryItemCatalogPort.fetch(groceryItemId)
 *     → HttpGroceryItemAdapter
 *     → CoreServerGroceryItemClient.findMetadataById(id)
 *     → core_server: GET /grocery-items/{id}/metadata
 * </pre>
 *
 * @author 승훈
 * @since 2026-05-15
 */
@HttpExchange("/grocery-items")
public interface CoreServerGroceryItemClient {

    /**
     * 식재료 단위 메타데이터 조회.
     *
     * @param id core_server GroceryItem ID (path variable로 String 전달, core_server에서 Long으로 파싱)
     * @return 단위 메타데이터 응답
     */
    @GetExchange("/{id}/metadata")
    CoreServerGroceryItemResponse findMetadataById(@PathVariable("id") String id);
}