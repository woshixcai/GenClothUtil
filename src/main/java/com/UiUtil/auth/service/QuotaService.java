package com.UiUtil.auth.service;

import com.UiUtil.auth.entity.SysUser;
import com.UiUtil.auth.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class QuotaService {

    @Autowired
    SysUserMapper sysUserMapper;

    /**
     * 查询今日剩余次数。-1 表示不限；0 表示已耗尽。
     */
    public int remainingToday(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return 0;
        if (user.getDailyQuota() == null || user.getDailyQuota() == -1) return -1;

        LocalDate today = LocalDate.now();
        java.sql.Date quotaDate = user.getQuotaDate() == null
                ? null : new java.sql.Date(user.getQuotaDate().getTime());
        boolean needReset = quotaDate == null || !quotaDate.toLocalDate().equals(today);
        int usedToday = needReset ? 0 : (user.getUsedToday() == null ? 0 : user.getUsedToday());
        return Math.max(0, user.getDailyQuota() - usedToday);
    }

    /**
     * 检查该用户是否有 AI 生图权限。
     */
    public void checkCanUseAi(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && Integer.valueOf(0).equals(user.getCanUseAi())) {
            throw new RuntimeException("该账号已被禁止使用 AI 生图功能");
        }
    }

    /**
     * 检查并消耗一次配额。
     * @return true=允许，false=超出配额
     */
    public boolean consumeQuota(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return false;

        if (user.getDailyQuota() == null || user.getDailyQuota() == -1) return true;

        LocalDate today = LocalDate.now();
        java.sql.Date quotaDate = user.getQuotaDate() == null
                ? null : new java.sql.Date(user.getQuotaDate().getTime());

        boolean needReset = quotaDate == null || !quotaDate.toLocalDate().equals(today);
        int usedToday = needReset ? 0 : (user.getUsedToday() == null ? 0 : user.getUsedToday());

        if (usedToday >= user.getDailyQuota()) return false;

        int updated = sysUserMapper.tryConsumeQuota(
                userId,
                needReset ? 1 : usedToday + 1,
                java.sql.Date.valueOf(today),
                needReset ? 0 : usedToday
        );
        return updated > 0;
    }
}
