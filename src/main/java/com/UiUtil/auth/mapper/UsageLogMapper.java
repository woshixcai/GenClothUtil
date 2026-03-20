package com.UiUtil.auth.mapper;

import com.UiUtil.auth.entity.UsageLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {

    @Select("SELECT shop_id, " +
            "SUM(CASE WHEN DATE(created_time)=CURDATE() THEN token_used ELSE 0 END) AS todayToken, " +
            "SUM(CASE WHEN DATE_FORMAT(created_time,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') THEN token_used ELSE 0 END) AS monthToken, " +
            "SUM(token_used) AS totalToken, " +
            "COUNT(CASE WHEN DATE(created_time)=CURDATE() THEN 1 END) AS todayCount, " +
            "COUNT(*) AS totalCount " +
            "FROM usage_log GROUP BY shop_id")
    List<Map<String, Object>> sumByShop();

    @Select("SELECT user_id, " +
            "SUM(CASE WHEN DATE(created_time)=CURDATE() THEN token_used ELSE 0 END) AS todayToken, " +
            "SUM(token_used) AS totalToken, " +
            "COUNT(*) AS totalCount, " +
            "MAX(created_time) AS lastActiveTime " +
            "FROM usage_log WHERE shop_id=#{shopId} GROUP BY user_id")
    List<Map<String, Object>> sumByUserInShop(@Param("shopId") Long shopId);
}
