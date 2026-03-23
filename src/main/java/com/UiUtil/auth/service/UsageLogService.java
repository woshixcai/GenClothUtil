package com.UiUtil.auth.service;

/**
 * Token 使用日志记录服务，在每次调用大模型后异步写入 usage_log 表，关联当前用户和所属店铺。
 */
import com.UiUtil.auth.entity.UsageLog;
import com.UiUtil.auth.mapper.SysUserMapper;
import com.UiUtil.auth.mapper.UsageLogMapper;
import com.UiUtil.shared.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class UsageLogService {

    @Autowired
    UsageLogMapper usageLogMapper;
    @Autowired
    SysUserMapper sysUserMapper;

    public void record(String action, int tokenUsed) {
        UserContext.LoginUser u = UserContext.current();
        if (u == null) return;

        UsageLog log = new UsageLog();
        log.setUserId(u.getUserId());
        log.setShopId(u.getShopId());
        log.setAction(action);
        log.setTokenUsed(tokenUsed);
        log.setCreatedTime(new Date());
        usageLogMapper.insert(log);

        sysUserMapper.addTotalToken(u.getUserId(), tokenUsed);
    }
}
