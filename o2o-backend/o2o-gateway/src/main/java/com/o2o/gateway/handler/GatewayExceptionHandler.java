package com.o2o.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关全局异常处理器，将网关抛出的异常（如404、503下游微服务未启动等）拦截并包装为友好的 JSON 结构返回给前端
 */
@Slf4j
@Order(-2) // 设定高优先级，确保在 Spring WebFlux 默认异常处理器之前执行
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 1. 如果 Response 已经提交（已经写入客户端），则直接向上抛出错误不作处理
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 2. 初始化默认的错误码与提示信息
        int code = 500;
        String message = "服务器内部错误，请稍后再试";

        // 3. 针对特定的异常类型进行细化解析
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException responseStatusException = (ResponseStatusException) ex;
            int statusValue = responseStatusException.getStatusCode().value();
            code = statusValue;
            
            if (statusValue == 404) {
                message = "接口路径不存在，请检查路由配置";
            } else if (statusValue == 503) {
                message = "下游服务暂时不可用，请稍后再试";
            } else {
                message = responseStatusException.getReason();
            }
        } else if (ex.getClass().getName().contains("ConnectException")
                || ex.getClass().getName().contains("TimeoutException")
                || ex.getMessage().contains("Connection refused")) {
            // 下游服务挂了或网络连接超时时，统一返回 503 Service Unavailable 友好消息
            code = 503;
            message = "系统服务暂时不可用，请检查下游微服务是否启动";
        }

        log.error("网关异常捕获：请求路径: {}, 异常类型: {}, 异常信息: {}",
                exchange.getRequest().getPath(), ex.getClass().getName(), ex.getMessage());

        // 4. 设置 HTTP 状态码与 Content-Type
        response.setStatusCode(HttpStatus.resolve(code) != null ? HttpStatus.resolve(code) : HttpStatus.INTERNAL_SERVER_ERROR);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 5. 组装与业务服务结构相同的统一 Result JSON 响应体
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("code", code);
        errorResult.put("message", message);
        errorResult.put("data", null);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResult);
            DataBufferFactory bufferFactory = response.bufferFactory();
            DataBuffer buffer = bufferFactory.wrap(bytes);
            // 将友好 JSON 刷回客户端
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("写出网关异常JSON失败", e);
            return Mono.error(ex);
        }
    }
}
