package com.refridge.fridge_management.config;

import com.refridge.fridge_management.fridge.infrastructure.grocery.CoreServerGroceryItemClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * core_server GroceryItem HTTP 클라이언트 빈 등록.
 *
 * <h2>Spring Boot 4 + Spring Framework 7 기반 RestClient + @HttpExchange</h2>
 * {@code HttpServiceProxyFactory} 빌더에는 timeout 설정이 없다.
 * timeout은 {@code RestClient}의 {@code requestFactory}에서 설정한다.
 *
 * <h2>application.yml 설정</h2>
 * <pre>
 * refridge:
 *   core-server:
 *     base-url: http://localhost:8080
 *     connect-timeout-ms: 3000
 *     read-timeout-ms: 5000
 * </pre>
 *
 * @author 승훈
 * @since 2026-04-26
 */
@Configuration
public class GroceryItemClientConfig {

    @Value("${refridge.core-server.base-url:http://localhost:8080}")
    private String coreServerBaseUrl;

    @Value("${refridge.core-server.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${refridge.core-server.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public CoreServerGroceryItemClient coreServerGroceryItemClient() {
        // timeout은 RestClient의 requestFactory 레벨에서 설정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient restClient = RestClient.builder()
                .baseUrl(coreServerBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(CoreServerGroceryItemClient.class);
    }
}