package com.UiUtil.auth.dto;

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
