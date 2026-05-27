package com.o2o.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器，从请求头中提取网关透传的当前用户 ID 并存入 ThreadLocal
 */
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 从请求头获取网关透传的用户 ID
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                UserContext.setUserId(Long.valueOf(userIdStr));
            } catch (NumberFormatException e) {
                // 如果格式不正确，记录日志或忽略即可
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) throws Exception {
        // 请求结束后必须清理 ThreadLocal，防止 Tomcat 线程池中线程被复用时读取到脏数据，或引起潜在的内存泄露
        UserContext.clear();
    }
}

