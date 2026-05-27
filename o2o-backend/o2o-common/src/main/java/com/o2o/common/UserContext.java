package com.o2o.common;

/**
 * 用户上下文对象，用于在同一线程内存储和获取当前登录用户的 ID
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置用户 ID 至当前线程上下文
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 从当前线程上下文获取用户 ID
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前线程上下文中的用户 ID（防止内存泄漏及线程池复用导致的数据污染）
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
