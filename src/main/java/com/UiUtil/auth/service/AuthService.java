package com.UiUtil.auth.service;

import cn.hutool.crypto.digest.BCrypt;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.auth.dto.LoginRequest;
import com.UiUtil.auth.dto.LoginResponse;
import com.UiUtil.auth.entity.SysPermission;
import com.UiUtil.auth.entity.SysUser;
import com.UiUtil.auth.mapper.SysPermissionMapper;
import com.UiUtil.auth.mapper.SysUserMapper;
import com.UiUtil.shared.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ──────────────────────────────────────────────────────────
    // 登录
    // ──────────────────────────────────────────────────────────

    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        if (!BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        List<String> permCodes = loadPermCodes(user.getId());
        String token = jwtUtil.generateToken(String.valueOf(user.getId()));

        return new LoginResponse(user.getId(), user.getUsername(), token, permCodes);
    }

    // ──────────────────────────────────────────────────────────
    // 注册（仅管理员调用，需配合 @RequirePermission("user:manage")）
    // ──────────────────────────────────────────────────────────

    public void register(String username, String rawPassword) {
        if (userMapper.findByUsername(username) != null) {
            throw new RuntimeException("用户名已存在：" + username);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        user.setStatus(1);
        user.setIsDeleted(0);
        user.setDailyQuota(5);   // 非会员每天免费试用 5 次
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userMapper.insert(user);
    }

    // ──────────────────────────────────────────────────────────
    // 工具方法：根据 userId 加载权限列表，供拦截器使用
    // ──────────────────────────────────────────────────────────

    public List<String> loadPermCodes(Long userId) {
        return permissionMapper.findPermsByUserId(userId)
                .stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toList());
    }

    /** 将用户信息写入线程上下文（由拦截器调用） */
    public void setUserContext(Long userId, String username) {
        SysUser user = userMapper.selectById(userId);
        UserContext.LoginUser loginUser = new UserContext.LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(username);
        loginUser.setPermissions(loadPermCodes(userId));
        if (user != null) {
            loginUser.setShopId(user.getShopId());
            loginUser.setCanSeeCost(user.getCanSeeCost());
        }
        UserContext.set(loginUser);
    }
}
