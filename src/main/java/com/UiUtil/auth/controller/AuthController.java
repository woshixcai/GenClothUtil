package com.UiUtil.auth.controller;

import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.auth.dto.LoginRequest;
import com.UiUtil.auth.dto.LoginResponse;
import com.UiUtil.shared.result.ApiResult;
import com.UiUtil.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 登录接口，无需鉴权。
     * POST /auth/login
     * Body: { "username": "admin", "password": "admin123" }
     */
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody @Validated LoginRequest req) {
        try {
            return ApiResult.ok(authService.login(req));
        } catch (RuntimeException e) {
            return ApiResult.fail(401, e.getMessage());
        }
    }

    /**
     * 退出登录：前端丢弃 Token 即可，服务端做日志记录（无状态 JWT 不需要服务端黑名单）。
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        return ApiResult.ok();
    }

    /**
     * 注册新用户（仅 ADMIN 角色可调用）。
     * POST /auth/register
     * Body: { "username": "xxx", "password": "xxx" }
     */
    @PostMapping("/register")
    @RequirePermission("user:manage")
    public ApiResult<Void> register(@RequestBody LoginRequest req) {
        try {
            authService.register(req.getUsername(), req.getPassword());
            return ApiResult.ok();
        } catch (RuntimeException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
