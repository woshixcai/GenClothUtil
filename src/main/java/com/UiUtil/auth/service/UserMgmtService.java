package com.UiUtil.auth.service;

import cn.hutool.crypto.digest.BCrypt;
import com.UiUtil.auth.entity.*;
import com.UiUtil.auth.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UserMgmtService {

    @Autowired SysUserMapper userMapper;
    @Autowired SysShopMapper shopMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired UsageLogMapper usageLogMapper;

    // 店铺编号生成：SHOPyyyyMMddHHmmss + 3位序号
    private static final AtomicInteger SHOP_SEQ  = new AtomicInteger(0);
    private static volatile String     SHOP_MARK = "";

    private static String genShopNo() {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        synchronized (UserMgmtService.class) {
            if (!ts.equals(SHOP_MARK)) { SHOP_MARK = ts; SHOP_SEQ.set(0); }
            return "SHOP" + ts + String.format("%03d", SHOP_SEQ.incrementAndGet());
        }
    }

    // ── 超管：查询所有店铺 ──────────────────────────────────
    public List<SysShop> listAllShops() {
        return shopMapper.selectList(
            new LambdaQueryWrapper<SysShop>().eq(SysShop::getIsDeleted, 0));
    }

    // ── 超管：开通店铺管理员（自动建店铺并绑定）────────────
    // roleId=4 SHOP_ADMIN，同时创建专属店铺
    public SysShop createShopAdmin(String username, String password, String shopName,
                                    Integer dailyQuota, Integer canSeeCost) {
        // 1. 创建店铺
        SysShop shop = new SysShop();
        shop.setShopNo(genShopNo());
        shop.setShopName(shopName != null && !shopName.isEmpty() ? shopName : username + "的店铺");
        shop.setStatus(1);
        shopMapper.insert(shop);

        // 2. 创建用户并绑定店铺
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setShopId(shop.getId());
        user.setDailyQuota(dailyQuota == null ? -1 : dailyQuota);
        user.setCanSeeCost(canSeeCost == null ? 1 : canSeeCost);  // 管理员默认可看进价
        user.setStatus(1);
        user.setIsDeleted(0);
        userMapper.insert(user);

        // 3. 绑定 SHOP_ADMIN 角色（role_id=4）
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(4L);
        userRoleMapper.insert(ur);

        return shop;
    }

    // ── 店铺管理员：开通子账号（自动继承当前操作人的店铺）──
    // roleId=5 SHOP_USER
    public void createSubUser(String username, String password,
                               Integer dailyQuota, Integer canSeeCost, Integer canUseAi) {
        UserContext.LoginUser operator = UserContext.current();
        Long shopId = operator != null ? operator.getShopId() : null;
        if (shopId == null) throw new RuntimeException("操作人未绑定店铺，无法创建子账号");

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setShopId(shopId);
        user.setDailyQuota(dailyQuota == null ? -1 : dailyQuota);
        user.setCanSeeCost(canSeeCost == null ? 0 : canSeeCost);
        user.setCanUseAi(canUseAi == null ? 1 : canUseAi);
        user.setStatus(1);
        user.setIsDeleted(0);
        userMapper.insert(user);

        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(5L);
        userRoleMapper.insert(ur);
    }

    public List<SysUser> listUsers() {
        UserContext.LoginUser operator = UserContext.current();
        Long shopId = operator != null ? operator.getShopId() : null;
        if (shopId == null) throw new RuntimeException("当前账号未绑定店铺");
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getShopId, shopId)
                        .eq(SysUser::getIsDeleted, 0));
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    public void updateUserSettings(Long targetUserId, Integer dailyQuota,
                                    Integer canSeeCost, Integer canUseAi) {
        SysUser update = new SysUser();
        update.setId(targetUserId);
        if (dailyQuota != null) update.setDailyQuota(dailyQuota);
        if (canSeeCost != null) update.setCanSeeCost(canSeeCost);
        if (canUseAi   != null) update.setCanUseAi(canUseAi);
        userMapper.updateById(update);
    }

    public List<Map<String, Object>> usageSummaryByShop() {
        return usageLogMapper.sumByShop();
    }

    public List<Map<String, Object>> usageSummaryByUser(Long shopId) {
        return usageLogMapper.sumByUserInShop(shopId);
    }
}
