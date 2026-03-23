package com.UiUtil.auth.dto;

/**
 * 登录请求参数 DTO，包含用户名和密码，均为必填项。
 */
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
