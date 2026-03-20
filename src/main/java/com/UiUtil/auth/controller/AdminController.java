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

    @RequirePermission("sys:manage")
    @PostMapping("/shops")
    public ApiResult<Void> createShop(@RequestParam String shopName) {
        userMgmtService.createShop(shopName);
        return ApiResult.ok();
    }

    @RequirePermission("user:shop_manage")
    @GetMapping("/shop/users")
    public ApiResult<List<SysUser>> listShopUsers(@RequestParam(required = false) Long shopId) {
        return ApiResult.ok(userMgmtService.listUsers(shopId));
    }

    @RequirePermission("user:shop_manage")
    @PostMapping("/shop/users")
    public ApiResult<Void> createUser(@RequestParam String username,
                                       @RequestParam String password,
                                       @RequestParam Long shopId,
                                       @RequestParam(defaultValue = "-1") Integer dailyQuota,
                                       @RequestParam(defaultValue = "0") Integer canSeeCost) {
        userMgmtService.createSubUser(username, password, shopId, dailyQuota, canSeeCost);
        return ApiResult.ok();
    }

    @RequirePermission("user:shop_manage")
    @PutMapping("/shop/users/{userId}")
    public ApiResult<Void> updateUserSettings(@PathVariable Long userId,
                                               @RequestParam(required = false) Integer dailyQuota,
                                               @RequestParam(required = false) Integer canSeeCost) {
        userMgmtService.updateUserSettings(userId, dailyQuota, canSeeCost);
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
