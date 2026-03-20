package com.UiUtil.shared.context;

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
