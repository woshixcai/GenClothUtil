package com.UiUtil.auth.service;

import cn.hutool.crypto.digest.BCrypt;
import com.UiUtil.auth.entity.*;
import com.UiUtil.auth.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class UserMgmtService {

    @Autowired SysUserMapper userMapper;
    @Autowired SysShopMapper shopMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired UsageLogMapper usageLogMapper;

    public List<SysShop> listAllShops() {
        return shopMapper.selectList(
            new LambdaQueryWrapper<SysShop>().eq(SysShop::getIsDeleted, 0));
    }

    public void createShop(String shopName) {
        SysShop shop = new SysShop();
        shop.setShopName(shopName);
        shop.setStatus(1);
        shopMapper.insert(shop);
    }

    public List<SysUser> listUsers(Long shopId) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0);
        if (shopId != null) q.eq(SysUser::getShopId, shopId);
        List<SysUser> users = userMapper.selectList(q);
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    public void createSubUser(String username, String password,
                               Long shopId, Integer dailyQuota, Integer canSeeCost) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setShopId(shopId);
        user.setDailyQuota(dailyQuota == null ? -1 : dailyQuota);
        user.setCanSeeCost(canSeeCost == null ? 0 : canSeeCost);
        user.setStatus(1);
        userMapper.insert(user);

        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(5L);
        userRoleMapper.insert(ur);
    }

    public void updateUserSettings(Long targetUserId, Integer dailyQuota, Integer canSeeCost) {
        SysUser update = new SysUser();
        update.setId(targetUserId);
        if (dailyQuota != null) update.setDailyQuota(dailyQuota);
        if (canSeeCost != null) update.setCanSeeCost(canSeeCost);
        userMapper.updateById(update);
    }

    public List<Map<String, Object>> usageSummaryByShop() {
        return usageLogMapper.sumByShop();
    }

    public List<Map<String, Object>> usageSummaryByUser(Long shopId) {
        return usageLogMapper.sumByUserInShop(shopId);
    }
}
