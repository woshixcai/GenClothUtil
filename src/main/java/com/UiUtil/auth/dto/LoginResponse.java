package com.UiUtil.auth.dto;

/**
 * 登录成功响应 DTO，包含 JWT Token、用户名及当前账号拥有的权限标识列表。
 */
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {

    private Long userId;
    private String username;
    private String token;
    /** 用户拥有的权限编码列表，如 ["image:generate", "image:recommend"] */
    private List<String> permissions;
}
