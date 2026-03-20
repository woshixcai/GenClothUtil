package com.UiUtil.shared.interceptor;

import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.auth.service.AuthService;
import com.UiUtil.shared.util.JwtUtil;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证 + RBAC 权限拦截器。
 *
 * 流程：
 *  1. 从请求头 Authorization: Bearer <token> 取出 Token
 *  2. JwtUtil 验证 Token 有效性，解析 userId
 *  3. 将用户信息（含权限列表）写入 UserContext
 *  4. 若方法标注了 @RequirePermission，校验用户是否拥有该权限
 *  5. 请求结束后清理 UserContext（afterCompletion）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // ── 1. 取 Token ─────────────────────────────────────────
        String token = resolveToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            writeJson(response, ApiResult.unauthorized());
            return false;
        }

        // ── 2. 解析用户，写入上下文 ──────────────────────────────
        Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));
        authService.setUserContext(userId, null);

        // ── 3. RBAC 权限校验 ─────────────────────────────────────
        HandlerMethod method = (HandlerMethod) handler;
        RequirePermission perm = method.getMethodAnnotation(RequirePermission.class);
        if (perm != null) {
            UserContext.LoginUser currentUser = UserContext.current();
            if (currentUser == null || !currentUser.hasPermission(perm.value())) {
                writeJson(response, ApiResult.forbidden());
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 从 Authorization: Bearer xxx 头中提取 Token */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private void writeJson(HttpServletResponse response, ApiResult<?> result) throws IOException {
        response.setStatus(result.getCode());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
