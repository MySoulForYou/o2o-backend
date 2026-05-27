package com.o2o.gateway.filter;

import com.o2o.gateway.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {

    // 路径匹配器
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 排除拦截的白名单路径（登录和注册接口不需要 Token，使用 /** 通配符兼容带有斜杠前缀的路径）
    private static final List<String> EXCLUDE_PATHS = List.of(
            "/**/user/login",
            "/**/user/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 安全清洗：强制清除请求中可能由客户端伪造的内部敏感请求头
        ServerHttpRequest cleanedRequest = request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove("X-User-Id");
                    httpHeaders.remove("X-User-Name");
                })
                .build();
        ServerWebExchange cleanedExchange = exchange.mutate().request(cleanedRequest).build();

        String path = cleanedRequest.getPath().toString();

        // 2. 白名单路径放行
        for (String excludePath : EXCLUDE_PATHS) {
            if (pathMatcher.match(excludePath, path)) {
                log.info("网关放行白名单接口: {}", path);
                return chain.filter(cleanedExchange);
            }
        }

        // 3. 从 Header 中获取 Authorization Token
        String token = cleanedRequest.getHeaders().getFirst("Authorization");

        // 兼容标准格式 "Bearer <token>"
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 4. 判断 Token 是否为空
        if (!StringUtils.hasText(token)) {
            log.warn("网关拦截成功：请求未携带 Token，路径: {}", path);
            return unauthorizedResponse(cleanedExchange);
        }

        // 5. 解析并校验 Token
        try {
            Claims claims = JwtUtils.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);

            // 6. 校验成功，在请求头中注入正确解密的用户信息，透传给下游服务
            ServerHttpRequest mutatedRequest = cleanedRequest.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Name", username)
                    .build();

            log.info("网关验签通过：用户已登录，userId: {}, username: {}, 准备转发请求", userId, username);

            // 将修改后的请求（带Header）放入上下文中，继续执行过滤链
            return chain.filter(cleanedExchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("网关拦截成功：Token 解析失败或已过期, 路径: {}, 原因: {}", path, e.getMessage());
            return unauthorizedResponse(cleanedExchange);
        }
    }

    /**
     * 向前端返回 401 状态码和友好 JSON 提示 (未登录)
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED); // 设置 401 状态码
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON); // 设置 Content-Type 为 JSON
        
        // 构造统一 Result 格式响应体
        String json = "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        
        return response.writeWith(Mono.just(buffer)); // 写入 JSON 响应体
    }

    @Override
    public int getOrder() {
        // 过滤器执行优先级，数字越小优先级越高
        return -1;
    }
}
