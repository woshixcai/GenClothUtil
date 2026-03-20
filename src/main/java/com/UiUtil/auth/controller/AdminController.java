package com.UiUtil.auth.controller;

import com.UiUtil.auth.entity.SysShop;
import com.UiUtil.auth.entity.SysUser;
import com.UiUtil.auth.service.UserMgmtService;
import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired UserMgmtService userMgmtService;

    @RequirePermission("sys:manage")
    @GetMapping("/shops")
    public ApiResult<List<SysShop>> listShops() {
        return ApiResult.ok(userMgmtService.listAllShops());
    }

    /** 超管：开通店铺管理员（自动创建店铺并绑定，返回店铺编号） */
    @RequirePermission("sys:manage")
    @PostMapping("/shop-admins")
    public ApiResult<SysShop> createShopAdmin(@RequestParam String username,
                                               @RequestParam String password,
                                               @RequestParam(required = false) String shopName,
                                               @RequestParam(defaultValue = "-1") Integer dailyQuota,
                                               @RequestParam(defaultValue = "1") Integer canSeeCost) {
        SysShop shop = userMgmtService.createShopAdmin(username, password, shopName, dailyQuota, canSeeCost);
        return ApiResult.ok(shop);
    }

    @RequirePermission("user:shop_manage")
    @GetMapping("/shop/users")
    public ApiResult<List<SysUser>> listShopUsers() {
        return ApiResult.ok(userMgmtService.listUsers());
    }

    /** 店铺管理员：开通子账号（自动继承当前操作人所在店铺，无需传 shopId） */
    @RequirePermission("user:shop_manage")
    @PostMapping("/shop/users")
    public ApiResult<Void> createUser(@RequestParam String username,
                                       @RequestParam String password,
                                       @RequestParam(defaultValue = "-1") Integer dailyQuota,
                                       @RequestParam(defaultValue = "0") Integer canSeeCost,
                                       @RequestParam(defaultValue = "1") Integer canUseAi) {
        userMgmtService.createSubUser(username, password, dailyQuota, canSeeCost, canUseAi);
        return ApiResult.ok();
    }

    @RequirePermission("user:shop_manage")
    @PutMapping("/shop/users/{userId}")
    public ApiResult<Void> updateUserSettings(@PathVariable Long userId,
                                               @RequestParam(required = false) Integer dailyQuota,
                                               @RequestParam(required = false) Integer canSeeCost,
                                               @RequestParam(required = false) Integer canUseAi) {
        userMgmtService.updateUserSettings(userId, dailyQuota, canSeeCost, canUseAi);
        return ApiResult.ok();
    }

    @RequirePermission("sys:manage")
    @GetMapping("/usage/shops")
    public ApiResult<List<Map<String, Object>>> usageByShop() {
        return ApiResult.ok(userMgmtService.usageSummaryByShop());
    }

    @RequirePermission("sys:manage")
    @GetMapping("/usage/shops/{shopId}/users")
    public ApiResult<List<Map<String, Object>>> usageByUser(@PathVariable Long shopId) {
        return ApiResult.ok(userMgmtService.usageSummaryByUser(shopId));
    }
}
