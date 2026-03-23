package com.UiUtil.shared.context;

/**
 * 用户上下文工具类，通过 ThreadLocal 在整个请求链路中传递当前登录用户的 ID、用户名、权限列表、
 * 店铺 ID 及进价可见权限等信息。请求结束后由拦截器负责清理，防止内存泄漏。
 */
import lombok.Data;

import java.util.List;

/**
 * 线程级别的用户上下文，由拦截器在请求进入时写入，结束后清理。
 * 业务层通过 UserContext.current() 获取当前登录用户信息。
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser current() {
        return HOLDER.get();
    }

    public static Long currentUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    public static class LoginUser {
        private Long userId;
        private String username;
        /** 该用户拥有的权限编码集合 */
        private List<String> permissions;
        private Long shopId;
        private Integer canSeeCost;

        public boolean hasPermission(String permCode) {
            return permissions != null && permissions.contains(permCode);
        }
    }
}
