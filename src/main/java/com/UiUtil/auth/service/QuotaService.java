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
