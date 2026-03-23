package com.UiUtil.auth.mapper;

/**
 * Token 使用日志数据访问层，提供按店铺/用户维度的月度消耗汇总查询。
 */
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

    /** 按店铺 + 月度统计 token 用量（关联店铺表取名称，列别名与前端 camelCase 一致） */
    @Select("SELECT ul.shop_id AS shopId, " +
            "MAX(s.shop_name) AS shopName, " +
            "DATE_FORMAT(ul.created_time,'%Y-%m') AS yearMonth, " +
            "SUM(ul.token_used) AS monthTokenUsed " +
            "FROM usage_log ul " +
            "LEFT JOIN sys_shop s ON s.id = ul.shop_id AND s.is_deleted = 0 " +
            "GROUP BY ul.shop_id, DATE_FORMAT(ul.created_time,'%Y-%m') " +
            "ORDER BY yearMonth DESC")
    List<Map<String, Object>> sumMonthlyByShop();

    /** 按店铺 + 月度统计用户 token 用量 */
    @Select("SELECT u.id AS user_id, u.username AS username, " +
            "DATE_FORMAT(ul.created_time,'%Y-%m') AS yearMonth, " +
            "SUM(ul.token_used) AS monthTokenUsed " +
            "FROM usage_log ul " +
            "JOIN sys_user u ON u.id = ul.user_id AND u.is_deleted=0 " +
            "WHERE ul.shop_id=#{shopId} " +
            "GROUP BY u.id, u.username, DATE_FORMAT(ul.created_time,'%Y-%m') " +
            "ORDER BY yearMonth DESC")
    List<Map<String, Object>> sumMonthlyByUserInShop(@Param("shopId") Long shopId);
}
