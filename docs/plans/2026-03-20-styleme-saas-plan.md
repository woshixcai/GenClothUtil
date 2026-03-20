# StyleMe SaaS v2.0 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有 StyleMe 换装工具升级为三级权限 SaaS 平台，新增换装反馈偏好学习和服装进销存拍照入库能力。

**Architecture:** 模块化单体，在现有 Spring Boot 基础上重组包结构为 auth / tryon / inventory / shared 四个业务域，单一部署单元，数据隔离靠 shop_id 强制过滤。

**Tech Stack:** Spring Boot 2.x · MyBatis Plus · MySQL 8 · 火山引擎 ARK（生图）· 阿里云 Dashscope（视觉识别）· 火山引擎 TOS（图片存储）· JWT · BCrypt · Web Speech API（浏览器原生）

**设计文档：** `docs/plans/2026-03-20-styleme-saas-design.md`

---

## 阶段一：基础重构（shared 模块 + 数据库变更）

### Task 1：数据库变更脚本

**Files:**
- Create: `src/main/resources/sql/v2_migration.sql`

**Step 1: 编写 SQL 迁移脚本**

```sql
-- ============================================================
-- StyleMe SaaS v2.0 数据库迁移脚本
-- ============================================================

-- 新增：店铺表
CREATE TABLE IF NOT EXISTS sys_shop (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_name   VARCHAR(100) NOT NULL COMMENT '店铺名称',
    status      TINYINT DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

-- 扩展：sys_user 新增字段
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS shop_id          BIGINT  DEFAULT NULL    COMMENT '所属店铺ID，超管为NULL',
    ADD COLUMN IF NOT EXISTS daily_quota      INT     DEFAULT -1      COMMENT '每日生图次数上限，-1不限',
    ADD COLUMN IF NOT EXISTS used_today       INT     DEFAULT 0       COMMENT '今日已使用次数',
    ADD COLUMN IF NOT EXISTS quota_date       DATE    DEFAULT NULL    COMMENT '次数统计日期，用于每日重置',
    ADD COLUMN IF NOT EXISTS can_see_cost     TINYINT DEFAULT 0       COMMENT '是否可查看进价 1是 0否',
    ADD COLUMN IF NOT EXISTS total_token_used BIGINT  DEFAULT 0       COMMENT '累计消耗Token数';

-- 新增：调用流水表
CREATE TABLE IF NOT EXISTS usage_log (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    shop_id      BIGINT      NOT NULL,
    action       VARCHAR(50) COMMENT '操作类型：tryon/recommend/ai_name',
    token_used   INT         DEFAULT 0,
    created_time DATETIME    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用量流水表';

-- 新增：生图记录表
CREATE TABLE IF NOT EXISTS tryon_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    shop_id       BIGINT       NOT NULL,
    result_urls   TEXT         COMMENT 'JSON数组，生成图片TOS URL',
    prompt_used   TEXT         COMMENT '本次实际发送给模型的完整prompt',
    pref_snapshot TEXT         COMMENT '本次带入的偏好快照JSON',
    style         VARCHAR(20),
    scene         VARCHAR(20),
    season        VARCHAR(20),
    token_used    INT          DEFAULT 0,
    status        TINYINT      DEFAULT 1,
    created_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    is_deleted    TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换装生图记录表';

-- 新增：反馈表
CREATE TABLE IF NOT EXISTS tryon_feedback (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id    BIGINT       NOT NULL COMMENT '关联 tryon_record.id',
    user_id      BIGINT       NOT NULL,
    tag_codes    VARCHAR(500) COMMENT '不满意标签，逗号分隔',
    extra_text   VARCHAR(500) COMMENT '用户补充文字（含语音转文字）',
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换装反馈表';

-- 新增：用户偏好权重表
CREATE TABLE IF NOT EXISTS user_preference (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL UNIQUE,
    tag_weights  TEXT   COMMENT 'JSON: {tag_code: 累计负向反馈次数}',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好权重表';

-- 新增：商品主表
CREATE TABLE IF NOT EXISTS cloth_item (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    item_name    VARCHAR(100)  NOT NULL,
    category     VARCHAR(50)   COMMENT '上装/下装/外套/裙装/配饰',
    cost_price   DECIMAL(10,2) DEFAULT NULL COMMENT '进价（权限控制）',
    sale_price   DECIMAL(10,2) DEFAULT NULL COMMENT '售价',
    description  VARCHAR(500)  COMMENT 'AI生成商品描述',
    status       TINYINT       DEFAULT 1 COMMENT '1上架 0下架',
    created_by   BIGINT        COMMENT '入库操作人user_id',
    created_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT       DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服装商品表';

-- 新增：商品图片表
CREATE TABLE IF NOT EXISTS cloth_image (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id      BIGINT       NOT NULL,
    tos_url      VARCHAR(500) NOT NULL,
    is_main      TINYINT      DEFAULT 0 COMMENT '1为主图',
    sort_order   INT          DEFAULT 0,
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

-- 新增：SKU表
CREATE TABLE IF NOT EXISTS cloth_sku (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id      BIGINT      NOT NULL,
    color        VARCHAR(30) COMMENT '颜色',
    size         VARCHAR(10) COMMENT '尺码',
    stock_qty    INT         DEFAULT 0,
    updated_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU库存表';

-- 新增权限数据
INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
    (3, 'SUPER_ADMIN', '超级管理员'),
    (4, 'SHOP_ADMIN',  '店铺管理员'),
    (5, 'SHOP_USER',   '店铺子账号');

INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, url, method) VALUES
    (4, 'inventory:manage', '管理服装库存',  '/inventory/**',       NULL),
    (5, 'inventory:cost',   '查看进价',      NULL,                  NULL),
    (6, 'user:shop_manage', '管理本店用户',  '/auth/shop/**',       NULL),
    (7, 'sys:manage',       '平台级管理',    '/admin/**',           NULL),
    (8, 'tryon:feedback',   '提交换装反馈',  '/tryon/feedback',     'POST');

-- 超级管理员拥有所有权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
    SELECT 3, id FROM sys_permission;

-- 店铺管理员：生图/推荐/库存管理/查看进价/管理用户
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES
    (4,1),(4,2),(4,4),(4,5),(4,6),(4,8);

-- 店铺子账号：生图/推荐/库存管理/反馈（进价由can_see_cost字段控制）
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES
    (5,1),(5,2),(5,4),(5,8);

-- 初始超级管理员（密码 admin123）
INSERT IGNORE INTO sys_user (id, username, password, status) VALUES
    (2, 'superadmin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (2, 3);
```

**Step 2: 在本地数据库执行脚本验证**

```
mysql -u root -p gen_cloth_ai < src/main/resources/sql/v2_migration.sql
```

预期：所有表创建成功，无报错

**Step 3: Commit**

```
git add src/main/resources/sql/v2_migration.sql
git commit -m "feat: add v2 database migration script"
```

---

### Task 2：重组包结构 & 迁移 shared 模块

**Files:**
- Create: `src/main/java/com/UiUtil/shared/annotation/RequirePermission.java`（迁移）
- Create: `src/main/java/com/UiUtil/shared/interceptor/AuthInterceptor.java`（迁移）
- Create: `src/main/java/com/UiUtil/shared/context/UserContext.java`（迁移）
- Create: `src/main/java/com/UiUtil/shared/result/ApiResult.java`（迁移）
- Create: `src/main/java/com/UiUtil/shared/util/`（迁移 JwtUtil/GenIdUtils/ImageUtils/AliyunUtils/VlcengineUtils）
- Modify: 所有引用旧包路径的文件（批量替换 import）

**Step 1: 在 IDE 中使用 Refactor → Move 迁移以下文件**

| 原路径 | 新路径 |
|--------|--------|
| `com.UiUtil.annotation.RequirePermission` | `com.UiUtil.shared.annotation.RequirePermission` |
| `com.UiUtil.interceptor.AuthInterceptor` | `com.UiUtil.shared.interceptor.AuthInterceptor` |
| `com.UiUtil.context.UserContext` | `com.UiUtil.shared.context.UserContext` |
| `com.UiUtil.Result.ApiResult` | `com.UiUtil.shared.result.ApiResult` |
| `com.UiUtil.Result.HuoShanResult` | `com.UiUtil.shared.result.HuoShanResult` |
| `com.UiUtil.uitl.*` | `com.UiUtil.shared.util.*` |

**Step 2: 验证编译**

```
mvn compile -q
```

预期：BUILD SUCCESS，0 errors

**Step 3: 同样迁移 auth 相关代码到 auth 子包**

| 原路径 | 新路径 |
|--------|--------|
| `com.UiUtil.entity.auth.*` | `com.UiUtil.auth.entity.*` |
| `com.UiUtil.mapper.auth.*` | `com.UiUtil.auth.mapper.*` |
| `com.UiUtil.service.AuthService` | `com.UiUtil.auth.service.AuthService` |
| `com.UiUtil.controller.AuthController` | `com.UiUtil.auth.controller.AuthController` |
| `com.UiUtil.dto.*` | `com.UiUtil.auth.dto.*` |

**Step 4: 验证编译 & 启动**

```
mvn compile -q
mvn spring-boot:run
```

预期：启动成功，访问 http://localhost:6688 页面正常

**Step 5: Commit**

```
git add -A
git commit -m "refactor: reorganize package structure into auth/tryon/inventory/shared"
```

---

## 阶段二：三级权限体系

### Task 3：SysShop 实体 & Mapper

**Files:**
- Create: `src/main/java/com/UiUtil/auth/entity/SysShop.java`
- Create: `src/main/java/com/UiUtil/auth/mapper/SysShopMapper.java`

**Step 1: 创建 SysShop 实体**

```java
package com.UiUtil.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("sys_shop")
public class SysShop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shopName;
    private Integer status;
    private Date createTime;
    @TableLogic
    private Integer isDeleted;
}
```

**Step 2: 创建 SysShopMapper**

```java
package com.UiUtil.auth.mapper;

import com.UiUtil.auth.entity.SysShop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysShopMapper extends BaseMapper<SysShop> {}
```

**Step 3: 扩展 SysUser 实体，添加新字段**

Modify: `src/main/java/com/UiUtil/auth/entity/SysUser.java`

在现有字段后追加：
```java
private Long shopId;
private Integer dailyQuota;    // -1 不限
private Integer usedToday;
private Date quotaDate;
private Integer canSeeCost;    // 0否 1是
private Long totalTokenUsed;
```

**Step 4: Commit**

```
git add -A
git commit -m "feat: add SysShop entity and extend SysUser with quota/cost fields"
```

---

### Task 4：用量流水记录（UsageLog）

**Files:**
- Create: `src/main/java/com/UiUtil/auth/entity/UsageLog.java`
- Create: `src/main/java/com/UiUtil/auth/mapper/UsageLogMapper.java`
- Create: `src/main/java/com/UiUtil/auth/service/UsageLogService.java`

**Step 1: 创建 UsageLog 实体**

```java
package com.UiUtil.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("usage_log")
public class UsageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private String action;   // tryon / recommend / ai_name
    private Integer tokenUsed;
    private Date createdTime;
}
```

**Step 2: 创建 UsageLogMapper**

```java
package com.UiUtil.auth.mapper;

import com.UiUtil.auth.entity.UsageLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {

    // 按店铺聚合：当日/当月/累计 token
    @Select("""
        SELECT shop_id,
               SUM(CASE WHEN DATE(created_time)=CURDATE() THEN token_used ELSE 0 END) AS todayToken,
               SUM(CASE WHEN DATE_FORMAT(created_time,'%Y-%m')=DATE_FORMAT(NOW(),'%Y-%m') THEN token_used ELSE 0 END) AS monthToken,
               SUM(token_used) AS totalToken,
               COUNT(CASE WHEN DATE(created_time)=CURDATE() THEN 1 END) AS todayCount,
               COUNT(*) AS totalCount
        FROM usage_log GROUP BY shop_id
    """)
    List<Map<String, Object>> sumByShop();

    // 某店铺内按用户聚合
    @Select("""
        SELECT user_id,
               SUM(CASE WHEN DATE(created_time)=CURDATE() THEN token_used ELSE 0 END) AS todayToken,
               SUM(token_used) AS totalToken,
               COUNT(*) AS totalCount,
               MAX(created_time) AS lastActiveTime
        FROM usage_log WHERE shop_id=#{shopId} GROUP BY user_id
    """)
    List<Map<String, Object>> sumByUserInShop(@Param("shopId") Long shopId);
}
```

**Step 3: 创建 UsageLogService**

```java
package com.UiUtil.auth.service;

import com.UiUtil.auth.entity.UsageLog;
import com.UiUtil.auth.mapper.UsageLogMapper;
import com.UiUtil.auth.mapper.SysUserMapper;
import com.UiUtil.shared.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class UsageLogService {

    @Autowired UsageLogMapper usageLogMapper;
    @Autowired SysUserMapper sysUserMapper;

    public void record(String action, int tokenUsed) {
        UserContext.LoginUser u = UserContext.current();
        if (u == null) return;

        UsageLog log = new UsageLog();
        log.setUserId(u.getUserId());
        log.setShopId(u.getShopId());   // LoginUser 需要新增 shopId 字段
        log.setAction(action);
        log.setTokenUsed(tokenUsed);
        log.setCreatedTime(new Date());
        usageLogMapper.insert(log);

        // 更新 sys_user.total_token_used
        sysUserMapper.addTotalToken(u.getUserId(), tokenUsed);
    }
}
```

**Step 4: SysUserMapper 添加更新 Token 方法**

Modify: `src/main/java/com/UiUtil/auth/mapper/SysUserMapper.java`

```java
@Update("UPDATE sys_user SET total_token_used = total_token_used + #{delta} WHERE id = #{userId}")
void addTotalToken(@Param("userId") Long userId, @Param("delta") int delta);
```

**Step 5: Commit**

```
git add -A
git commit -m "feat: add UsageLog entity, mapper and service for token tracking"
```

---

### Task 5：每日配额校验 & 重置

**Files:**
- Create: `src/main/java/com/UiUtil/auth/service/QuotaService.java`

**Step 1: 创建 QuotaService**

```java
package com.UiUtil.auth.service;

import com.UiUtil.auth.entity.SysUser;
import com.UiUtil.auth.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Date;

@Service
public class QuotaService {

    @Autowired SysUserMapper sysUserMapper;

    /**
     * 检查并消耗一次配额。
     * @return true=允许，false=超出配额
     */
    public boolean consumeQuota(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return false;

        // -1 表示不限次数
        if (user.getDailyQuota() == null || user.getDailyQuota() == -1) return true;

        // 判断是否需要重置（日期变更）
        LocalDate today = LocalDate.now();
        java.sql.Date quotaDate = user.getQuotaDate() == null
                ? null : new java.sql.Date(user.getQuotaDate().getTime());

        boolean needReset = quotaDate == null || !quotaDate.toLocalDate().equals(today);

        int usedToday = needReset ? 0 : (user.getUsedToday() == null ? 0 : user.getUsedToday());

        if (usedToday >= user.getDailyQuota()) return false;

        // 原子更新（防并发超用）
        int updated = sysUserMapper.tryConsumeQuota(
                userId,
                needReset ? 1 : usedToday + 1,
                java.sql.Date.valueOf(today),
                needReset ? 0 : usedToday   // 乐观锁条件
        );
        return updated > 0;
    }
}
```

**Step 2: SysUserMapper 添加原子更新方法**

Modify: `src/main/java/com/UiUtil/auth/mapper/SysUserMapper.java`

```java
// needReset=true 时 expectUsed=0；false 时 expectUsed=当前值（乐观锁）
@Update("""
    UPDATE sys_user
    SET used_today = #{newUsed}, quota_date = #{today}
    WHERE id = #{userId}
      AND (quota_date IS NULL OR quota_date != #{today} OR used_today = #{expectUsed})
""")
int tryConsumeQuota(@Param("userId") Long userId,
                    @Param("newUsed") int newUsed,
                    @Param("today") java.sql.Date today,
                    @Param("expectUsed") int expectUsed);
```

**Step 3: Commit**

```
git add -A
git commit -m "feat: add daily quota check and atomic consume logic"
```

---

### Task 6：管理员 API（店铺管理 & 用量看板 & 用户管理）

**Files:**
- Create: `src/main/java/com/UiUtil/auth/controller/AdminController.java`
- Create: `src/main/java/com/UiUtil/auth/service/UserMgmtService.java`

**Step 1: 创建 UserMgmtService**

```java
package com.UiUtil.auth.service;

import com.UiUtil.auth.entity.*;
import com.UiUtil.auth.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class UserMgmtService {

    @Autowired SysUserMapper userMapper;
    @Autowired SysShopMapper shopMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired UsageLogMapper usageLogMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // 超管：查所有店铺
    public List<SysShop> listAllShops() {
        return shopMapper.selectList(
            new LambdaQueryWrapper<SysShop>().eq(SysShop::getIsDeleted, 0));
    }

    // 超管：创建店铺
    public void createShop(String shopName) {
        SysShop shop = new SysShop();
        shop.setShopName(shopName);
        shop.setStatus(1);
        shopMapper.insert(shop);
    }

    // 超管/店铺管理员：列出本店或全部用户
    public List<SysUser> listUsers(Long shopId) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0);
        if (shopId != null) q.eq(SysUser::getShopId, shopId);
        List<SysUser> users = userMapper.selectList(q);
        users.forEach(u -> u.setPassword(null)); // 不返回密码
        return users;
    }

    // 店铺管理员：创建子账号
    public void createSubUser(String username, String password,
                               Long shopId, Integer dailyQuota, Integer canSeeCost) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setShopId(shopId);
        user.setDailyQuota(dailyQuota == null ? -1 : dailyQuota);
        user.setCanSeeCost(canSeeCost == null ? 0 : canSeeCost);
        user.setStatus(1);
        userMapper.insert(user);
        // 绑定 SHOP_USER 角色（role_id=5）
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(5L);
        userRoleMapper.insert(ur);
    }

    // 店铺管理员：修改子账号配额和进价权限
    public void updateUserSettings(Long targetUserId, Integer dailyQuota, Integer canSeeCost) {
        SysUser update = new SysUser();
        update.setId(targetUserId);
        if (dailyQuota != null)  update.setDailyQuota(dailyQuota);
        if (canSeeCost != null)  update.setCanSeeCost(canSeeCost);
        userMapper.updateById(update);
    }

    // 用量看板：店铺汇总
    public List<Map<String, Object>> usageSummaryByShop() {
        return usageLogMapper.sumByShop();
    }

    // 用量看板：某店铺内用户明细
    public List<Map<String, Object>> usageSummaryByUser(Long shopId) {
        return usageLogMapper.sumByUserInShop(shopId);
    }
}
```

**Step 2: 创建 AdminController**

```java
package com.UiUtil.auth.controller;

import com.UiUtil.auth.entity.*;
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

    // 超管：查询所有店铺
    @RequirePermission("sys:manage")
    @GetMapping("/shops")
    public ApiResult<List<SysShop>> listShops() {
        return ApiResult.ok(userMgmtService.listAllShops());
    }

    // 超管：创建店铺
    @RequirePermission("sys:manage")
    @PostMapping("/shops")
    public ApiResult<Void> createShop(@RequestParam String shopName) {
        userMgmtService.createShop(shopName);
        return ApiResult.ok();
    }

    // 店铺管理员：查看本店用户列表
    @RequirePermission("user:shop_manage")
    @GetMapping("/shop/users")
    public ApiResult<List<SysUser>> listShopUsers(@RequestParam(required = false) Long shopId) {
        return ApiResult.ok(userMgmtService.listUsers(shopId));
    }

    // 店铺管理员：创建子账号
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

    // 店铺管理员：修改子账号配额/进价权限
    @RequirePermission("user:shop_manage")
    @PutMapping("/shop/users/{userId}")
    public ApiResult<Void> updateUserSettings(@PathVariable Long userId,
                                               @RequestParam(required = false) Integer dailyQuota,
                                               @RequestParam(required = false) Integer canSeeCost) {
        userMgmtService.updateUserSettings(userId, dailyQuota, canSeeCost);
        return ApiResult.ok();
    }

    // 超管：用量看板 - 店铺汇总
    @RequirePermission("sys:manage")
    @GetMapping("/usage/shops")
    public ApiResult<List<Map<String, Object>>> usageByShop() {
        return ApiResult.ok(userMgmtService.usageSummaryByShop());
    }

    // 超管：用量看板 - 某店铺内账号明细
    @RequirePermission("sys:manage")
    @GetMapping("/usage/shops/{shopId}/users")
    public ApiResult<List<Map<String, Object>>> usageByUser(@PathVariable Long shopId) {
        return ApiResult.ok(userMgmtService.usageSummaryByUser(shopId));
    }
}
```

**Step 3: 验证编译**

```
mvn compile -q
```

预期：BUILD SUCCESS

**Step 4: Commit**

```
git add -A
git commit -m "feat: add admin controller for shop/user management and usage dashboard"
```

---

## 阶段三：换装反馈 + 偏好学习

### Task 7：Tryon 模块实体 & Mapper

**Files:**
- Create: `src/main/java/com/UiUtil/tryon/entity/TryonRecord.java`
- Create: `src/main/java/com/UiUtil/tryon/entity/TryonFeedback.java`
- Create: `src/main/java/com/UiUtil/tryon/entity/UserPreference.java`
- Create: `src/main/java/com/UiUtil/tryon/mapper/TryonRecordMapper.java`
- Create: `src/main/java/com/UiUtil/tryon/mapper/TryonFeedbackMapper.java`
- Create: `src/main/java/com/UiUtil/tryon/mapper/UserPreferenceMapper.java`

**Step 1: TryonRecord 实体**

```java
package com.UiUtil.tryon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("tryon_record")
public class TryonRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shopId;
    private String resultUrls;    // JSON数组字符串
    private String promptUsed;
    private String prefSnapshot;  // 带入的偏好快照JSON
    private String style;
    private String scene;
    private String season;
    private Integer tokenUsed;
    private Integer status;
    private Date createdTime;
    @TableLogic
    private Integer isDeleted;
}
```

**Step 2: TryonFeedback 实体**

```java
package com.UiUtil.tryon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("tryon_feedback")
public class TryonFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long userId;
    private String tagCodes;   // 逗号分隔，如 model_fake,pose_stiff
    private String extraText;
    private Date createdTime;
}
```

**Step 3: UserPreference 实体**

```java
package com.UiUtil.tryon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_preference")
public class UserPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tagWeights;   // JSON: {"model_fake":5,"pose_stiff":3}
    private Date updatedTime;
}
```

**Step 4: 创建三个 Mapper（均继承 BaseMapper）**

```java
// TryonRecordMapper.java
@Mapper
public interface TryonRecordMapper extends BaseMapper<TryonRecord> {}

// TryonFeedbackMapper.java
@Mapper
public interface TryonFeedbackMapper extends BaseMapper<TryonFeedback> {}

// UserPreferenceMapper.java
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {}
```

**Step 5: Commit**

```
git add -A
git commit -m "feat: add tryon module entities and mappers"
```

---

### Task 8：偏好标签体系 & Prompt 注入服务

**Files:**
- Create: `src/main/java/com/UiUtil/tryon/service/PreferenceService.java`

**Step 1: 创建 PreferenceService（包含标签映射和 prompt 注入）**

```java
package com.UiUtil.tryon.service;

import com.UiUtil.tryon.entity.UserPreference;
import com.UiUtil.tryon.mapper.UserPreferenceMapper;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PreferenceService {

    @Autowired UserPreferenceMapper preferenceMapper;

    /** tag_code → 中文负向引导语 */
    private static final Map<String, String> TAG_GUIDANCE = new LinkedHashMap<>();
    static {
        TAG_GUIDANCE.put("model_fake",        "模特看起来像AI合成，请确保皮肤纹理自然真实，避免过于完美的假脸");
        TAG_GUIDANCE.put("pose_stiff",        "姿势太僵硬，请生成自然放松的站姿或动态感姿势");
        TAG_GUIDANCE.put("body_distorted",    "人物比例失真，请保持真实自然的身材比例，避免拉伸变形");
        TAG_GUIDANCE.put("face_blur",         "面部模糊不自然，请提升面部清晰度和真实感");
        TAG_GUIDANCE.put("cloth_unflattering","衣服不显身材，请展示衣物对身形的修饰效果");
        TAG_GUIDANCE.put("color_off",         "颜色色调偏差，请保持衣物色彩的真实准确性");
        TAG_GUIDANCE.put("texture_blur",      "材质纹理模糊，请清晰呈现织物的真实材质感");
        TAG_GUIDANCE.put("detail_lost",       "衣物细节（纽扣/图案/刺绣）丢失，请保留所有服装细节");
        TAG_GUIDANCE.put("scene_dislike",     "背景场景不喜欢，请使用简洁自然的背景");
        TAG_GUIDANCE.put("lighting_bad",      "光线不自然，请使用柔和均匀的自然光效果");
        TAG_GUIDANCE.put("bg_distracting",    "背景太抢眼，请降低背景复杂度，突出服装主体");
        TAG_GUIDANCE.put("style_mismatch",    "整体风格不对，请严格按照所选风格和场景生成");
        TAG_GUIDANCE.put("ref_mismatch",      "与参考图差距太大，请更贴近参考穿搭的构图和风格");
    }

    /**
     * 根据用户偏好权重生成 prompt 前缀注入文本。
     * 取权重 Top 3 标签，返回空字符串表示无偏好数据。
     */
    public String buildPrefPromptPrefix(Long userId) {
        UserPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));
        if (pref == null || pref.getTagWeights() == null) return "";

        Map<String, Integer> weights = JSON.parseObject(
                pref.getTagWeights(), new TypeReference<Map<String, Integer>>() {});
        if (weights == null || weights.isEmpty()) return "";

        List<Map.Entry<String, Integer>> top3 = weights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder("【用户偏好优化】该用户历史反馈需重点改善：\n");
        for (int i = 0; i < top3.size(); i++) {
            Map.Entry<String, Integer> e = top3.get(i);
            String guidance = TAG_GUIDANCE.getOrDefault(e.getKey(), e.getKey());
            sb.append(" - ").append(guidance)
              .append("（反馈").append(e.getValue()).append("次）;\n");
        }
        sb.append("以上为优先改善方向，其余要求如下：\n");
        return sb.toString();
    }

    /**
     * 更新用户偏好权重（每个 tag +1）
     */
    public void updateWeights(Long userId, List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) return;

        UserPreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));

        Map<String, Integer> weights = new HashMap<>();
        if (pref != null && pref.getTagWeights() != null) {
            weights = JSON.parseObject(pref.getTagWeights(),
                    new TypeReference<Map<String, Integer>>() {});
        }

        for (String tag : tagCodes) {
            weights.merge(tag, 1, Integer::sum);
        }

        String newWeightsJson = JSON.toJSONString(weights);
        if (pref == null) {
            UserPreference newPref = new UserPreference();
            newPref.setUserId(userId);
            newPref.setTagWeights(newWeightsJson);
            preferenceMapper.insert(newPref);
        } else {
            pref.setTagWeights(newWeightsJson);
            preferenceMapper.updateById(pref);
        }
    }
}
```

**Step 2: Commit**

```
git add -A
git commit -m "feat: add PreferenceService with tag guidance and prompt injection"
```

---

### Task 9：换装主流程集成（配额 + 记录 + Token 上报）

**Files:**
- Modify: `src/main/java/com/UiUtil/tryon/service/TryonService.java`（从 GenImageService 重构而来）
- Modify: `src/main/java/com/UiUtil/tryon/controller/TryonController.java`（从 TestController 重构而来）

**Step 1: 创建 TryonService（整合配额/偏好/记录/Token上报）**

```java
package com.UiUtil.tryon.service;

import com.UiUtil.auth.service.QuotaService;
import com.UiUtil.auth.service.UsageLogService;
import com.UiUtil.shared.result.HuoShanResult;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.tryon.entity.TryonRecord;
import com.UiUtil.tryon.mapper.TryonRecordMapper;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class TryonService {

    @Autowired QuotaService quotaService;
    @Autowired PreferenceService preferenceService;
    @Autowired UsageLogService usageLogService;
    @Autowired TryonRecordMapper recordMapper;

    // 注入原有 GenImageService 的依赖
    @Autowired com.UiUtil.shared.util.VlcengineUtils vlcengineUtils;
    @Autowired com.UiUtil.shared.util.ImageUtils imageUtils;
    @Autowired com.UiUtil.mapper.UploadImageCacheMapper uploadImageCacheMapper;

    public Map<String, Object> recommend(List<MultipartFile> clothesFiles,
                                          List<MultipartFile> referenceFiles,
                                          String style, String scene, String season) {
        Map<String, Object> resp = new HashMap<>();
        UserContext.LoginUser user = UserContext.current();

        // 配额校验
        if (!quotaService.consumeQuota(user.getUserId())) {
            resp.put("recommendText", "今日生图次数已达上限，请联系管理员调整配额");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }

        MultipartFile clothes   = (clothesFiles   == null || clothesFiles.isEmpty())   ? null : clothesFiles.get(0);
        MultipartFile reference = (referenceFiles == null || referenceFiles.isEmpty()) ? null : referenceFiles.get(0);

        if (clothes == null || clothes.isEmpty()) {
            resp.put("recommendText", "请至少上传1张需要穿版的衣服图片");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }
        if (reference == null || reference.isEmpty()) {
            resp.put("recommendText", "请至少上传1张参考穿搭图片");
            resp.put("recommendImgs", Collections.emptyList());
            return resp;
        }

        // 带入用户偏好构建 prompt
        String prefPrefix = preferenceService.buildPrefPromptPrefix(user.getUserId());
        String basePrompt = "请基于参考穿搭图与衣服图生成真实自然的试穿效果图。" +
                "要求：风格为" + safe(style) + "，适用场景为" + safe(scene) + "，季节为" + safe(season) + "。" +
                "保持人物比例自然、衣物材质与纹理清晰、光照一致、背景不过度抢眼。";
        String finalPrompt = prefPrefix + basePrompt;

        HuoShanResult result = genImage(reference, clothes, finalPrompt);

        // 记录流水 & 更新 token
        int tokenUsed = result.getTokenUsed() == null ? 0 : result.getTokenUsed();
        usageLogService.record("recommend", tokenUsed);

        // 保存生图记录
        TryonRecord record = new TryonRecord();
        record.setUserId(user.getUserId());
        record.setShopId(user.getShopId());
        record.setResultUrls(result.isSuccess() ? JSON.toJSONString(result.getImageUrls()) : null);
        record.setPromptUsed(finalPrompt);
        record.setPrefSnapshot(preferenceService.buildPrefPromptPrefix(user.getUserId()));
        record.setStyle(style);
        record.setScene(scene);
        record.setSeason(season);
        record.setTokenUsed(tokenUsed);
        record.setCreatedTime(new Date());
        recordMapper.insert(record);

        if (result.isSuccess()) {
            resp.put("recordId", record.getId());  // 返回 recordId 供反馈使用
            resp.put("recommendText", "已为你生成穿搭试衣效果图（风格：" + safe(style) + "）。");
            resp.put("recommendImgs", result.getImageUrls());
        } else {
            resp.put("recommendText", result.getErrorMsg() == null ? "生成失败，请稍后重试" : result.getErrorMsg());
            resp.put("recommendImgs", Collections.emptyList());
        }
        resp.put("uploadSecond",   result.getUploadSecond());
        resp.put("generateSecond", result.getGenerateSecond());
        resp.put("totalSecond",    result.getTotalSecond());
        return resp;
    }

    private HuoShanResult genImage(MultipartFile demoFile, MultipartFile closeFile, String prompt) {
        // 委托现有 GenImageService 逻辑（MD5缓存 + TOS上传 + 生图）
        // 此处直接复用 GenImageService.genImage，避免重复代码
        // 后续可合并进 TryonService
        try {
            return new com.UiUtil.service.GenImageService().genImage(demoFile, closeFile, prompt);
        } catch (Exception e) {
            HuoShanResult r = new HuoShanResult();
            r.setSuccess(false);
            r.setErrorMsg(e.getMessage());
            return r;
        }
    }

    private static String safe(String v) {
        return (v == null || v.trim().isEmpty()) ? "未指定" : v.trim();
    }
}
```

> **注意：** 实际实现时将 GenImageService 内部逻辑直接迁移到 TryonService，不要 new 对象，通过 @Autowired 注入所有依赖。

**Step 2: Commit**

```
git add -A
git commit -m "feat: add TryonService integrating quota, preference prompt, and usage logging"
```

---

### Task 10：反馈 API

**Files:**
- Create: `src/main/java/com/UiUtil/tryon/controller/TryonController.java`
- Create: `src/main/java/com/UiUtil/tryon/service/FeedbackService.java`

**Step 1: 创建 FeedbackService**

```java
package com.UiUtil.tryon.service;

import com.UiUtil.tryon.entity.TryonFeedback;
import com.UiUtil.tryon.mapper.TryonFeedbackMapper;
import com.UiUtil.shared.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FeedbackService {

    @Autowired TryonFeedbackMapper feedbackMapper;
    @Autowired PreferenceService preferenceService;

    public void submitFeedback(Long recordId, List<String> tagCodes, String extraText) {
        UserContext.LoginUser user = UserContext.current();

        TryonFeedback fb = new TryonFeedback();
        fb.setRecordId(recordId);
        fb.setUserId(user.getUserId());
        fb.setTagCodes(tagCodes == null ? "" : String.join(",", tagCodes));
        fb.setExtraText(extraText);
        fb.setCreatedTime(new Date());
        feedbackMapper.insert(fb);

        // 更新偏好权重
        preferenceService.updateWeights(user.getUserId(), tagCodes);
    }
}
```

**Step 2: 创建 TryonController**

```java
package com.UiUtil.tryon.controller;

import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import com.UiUtil.tryon.service.FeedbackService;
import com.UiUtil.tryon.service.TryonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/tryon")
public class TryonController {

    @Autowired TryonService tryonService;
    @Autowired FeedbackService feedbackService;

    @RequirePermission("image:recommend")
    @PostMapping("/recommend")
    public ApiResult<Map<String, Object>> recommend(
            @RequestParam(required = false) List<MultipartFile> clothesFiles,
            @RequestParam(required = false) List<MultipartFile> referenceFiles,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String season) {
        return ApiResult.ok(tryonService.recommend(clothesFiles, referenceFiles, style, scene, season));
    }

    @RequirePermission("tryon:feedback")
    @PostMapping("/feedback")
    public ApiResult<Void> feedback(@RequestParam Long recordId,
                                     @RequestParam(required = false) List<String> tagCodes,
                                     @RequestParam(required = false) String extraText) {
        feedbackService.submitFeedback(recordId, tagCodes, extraText);
        return ApiResult.ok();
    }
}
```

**Step 3: 验证编译**

```
mvn compile -q
```

**Step 4: Commit**

```
git add -A
git commit -m "feat: add feedback API and preference weight update logic"
```

---

## 阶段四：服装进销存

### Task 11：Inventory 模块实体 & Mapper

**Files:**
- Create: `src/main/java/com/UiUtil/inventory/entity/ClothItem.java`
- Create: `src/main/java/com/UiUtil/inventory/entity/ClothImage.java`
- Create: `src/main/java/com/UiUtil/inventory/entity/ClothSku.java`
- Create: `src/main/java/com/UiUtil/inventory/mapper/ClothItemMapper.java`
- Create: `src/main/java/com/UiUtil/inventory/mapper/ClothImageMapper.java`
- Create: `src/main/java/com/UiUtil/inventory/mapper/ClothSkuMapper.java`

**Step 1: ClothItem 实体**

```java
package com.UiUtil.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cloth_item")
public class ClothItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String itemName;
    private String category;
    private BigDecimal costPrice;   // 权限控制，可能为null返回
    private BigDecimal salePrice;
    private String description;
    private Integer status;         // 1上架 0下架
    private Long createdBy;
    private Date createdTime;
    private Date updatedTime;
    @TableLogic
    private Integer isDeleted;
}
```

**Step 2: ClothImage & ClothSku 实体**

```java
// ClothImage.java
@Data @TableName("cloth_image")
public class ClothImage {
    @TableId(type = IdType.AUTO) private Long id;
    private Long itemId;
    private String tosUrl;
    private Integer isMain;      // 1主图
    private Integer sortOrder;
    private Date createdTime;
}

// ClothSku.java
@Data @TableName("cloth_sku")
public class ClothSku {
    @TableId(type = IdType.AUTO) private Long id;
    private Long itemId;
    private String color;
    private String size;
    private Integer stockQty;
    private Date updatedTime;
}
```

**Step 3: 三个 Mapper（均继承 BaseMapper）**

```java
@Mapper public interface ClothItemMapper  extends BaseMapper<ClothItem>  {}
@Mapper public interface ClothImageMapper extends BaseMapper<ClothImage> {}
@Mapper public interface ClothSkuMapper   extends BaseMapper<ClothSku>   {}
```

**Step 4: Commit**

```
git add -A
git commit -m "feat: add inventory module entities and mappers"
```

---

### Task 12：AI 取名服务 & 拍照入库 API

**Files:**
- Create: `src/main/java/com/UiUtil/inventory/service/InventoryService.java`
- Create: `src/main/java/com/UiUtil/inventory/controller/InventoryController.java`

**Step 1: 创建 InventoryService**

```java
package com.UiUtil.inventory.service;

import com.UiUtil.inventory.entity.*;
import com.UiUtil.inventory.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.shared.util.AliyunUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;

@Service
public class InventoryService {

    @Autowired AliyunUtils aliyunUtils;
    @Autowired ImageUtils imageUtils;
    @Autowired ClothItemMapper itemMapper;
    @Autowired ClothImageMapper imageMapper;
    @Autowired ClothSkuMapper skuMapper;

    private static final String AI_NAME_PROMPT =
            "请识别这件服装，用10字以内给出一个简洁的商品名称，格式为「颜色+款式」，" +
            "例如「米白色宽松休闲卫衣」。同时用一句话（50字以内）描述这件衣服的特点。" +
            "按以下格式返回，不要其他内容：\n名称：xxx\n描述：xxx";

    /**
     * AI 识别图片，返回建议名称和描述
     */
    public Map<String, String> aiRecognize(MultipartFile image) throws Exception {
        String raw = aliyunUtils.qWenVLPlus(image, AI_NAME_PROMPT);
        Map<String, String> result = new HashMap<>();
        // 解析格式：名称：xxx\n描述：xxx
        for (String line : raw.split("\n")) {
            if (line.startsWith("名称：") || line.startsWith("名称:")) {
                result.put("name", line.replaceFirst("名称[：:]", "").trim());
            } else if (line.startsWith("描述：") || line.startsWith("描述:")) {
                result.put("description", line.replaceFirst("描述[：:]", "").trim());
            }
        }
        if (!result.containsKey("name")) result.put("name", "未识别商品");
        if (!result.containsKey("description")) result.put("description", "");
        return result;
    }

    /**
     * 入库：保存商品 + 图片 + SKU
     */
    public Long saveItem(MultipartFile mainImage,
                          String itemName,
                          String category,
                          BigDecimal costPrice,
                          BigDecimal salePrice,
                          String description,
                          String color,
                          String size,
                          Integer stockQty) throws Exception {
        UserContext.LoginUser user = UserContext.current();

        // 上传主图到 TOS
        String tosUrl = imageUtils.uploadFileToHuoShan(mainImage);

        // 保存商品
        ClothItem item = new ClothItem();
        item.setShopId(user.getShopId());
        item.setItemName(itemName);
        item.setCategory(category);
        item.setCostPrice(costPrice);
        item.setSalePrice(salePrice);
        item.setDescription(description);
        item.setStatus(1);
        item.setCreatedBy(user.getUserId());
        item.setCreatedTime(new Date());
        itemMapper.insert(item);

        // 保存图片
        ClothImage img = new ClothImage();
        img.setItemId(item.getId());
        img.setTosUrl(tosUrl);
        img.setIsMain(1);
        img.setSortOrder(0);
        img.setCreatedTime(new Date());
        imageMapper.insert(img);

        // 保存 SKU
        if (color != null || size != null) {
            ClothSku sku = new ClothSku();
            sku.setItemId(item.getId());
            sku.setColor(color);
            sku.setSize(size);
            sku.setStockQty(stockQty == null ? 0 : stockQty);
            skuMapper.insert(sku);
        }

        return item.getId();
    }

    /**
     * 查询本店商品列表（根据 can_see_cost 决定是否返回进价）
     */
    public List<ClothItem> listItems(String category, Integer status) {
        UserContext.LoginUser user = UserContext.current();
        LambdaQueryWrapper<ClothItem> q = new LambdaQueryWrapper<ClothItem>()
                .eq(ClothItem::getShopId, user.getShopId())
                .eq(ClothItem::getIsDeleted, 0);
        if (category != null) q.eq(ClothItem::getCategory, category);
        if (status != null)   q.eq(ClothItem::getStatus, status);
        List<ClothItem> items = itemMapper.selectList(q);

        // 权限过滤：不可查看进价则置 null
        if (user.getCanSeeCost() == null || user.getCanSeeCost() == 0) {
            items.forEach(i -> i.setCostPrice(null));
        }
        return items;
    }

    /**
     * 调整 SKU 库存
     */
    public void updateStock(Long skuId, int delta) {
        ClothSku sku = skuMapper.selectById(skuId);
        if (sku == null) throw new RuntimeException("SKU不存在");
        sku.setStockQty(Math.max(0, sku.getStockQty() + delta));
        skuMapper.updateById(sku);
    }

    /**
     * 下架商品（仅管理员）
     */
    public void setItemStatus(Long itemId, int status) {
        ClothItem update = new ClothItem();
        update.setId(itemId);
        update.setStatus(status);
        itemMapper.updateById(update);
    }
}
```

**Step 2: 创建 InventoryController**

```java
package com.UiUtil.inventory.controller;

import com.UiUtil.inventory.entity.ClothItem;
import com.UiUtil.inventory.service.InventoryService;
import com.UiUtil.shared.annotation.RequirePermission;
import com.UiUtil.shared.result.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired InventoryService inventoryService;

    /** AI 识别图片，返回建议名称和描述（入库前调用）*/
    @RequirePermission("inventory:manage")
    @PostMapping("/recognize")
    public ApiResult<Map<String, String>> recognize(@RequestParam("image") MultipartFile image) throws Exception {
        return ApiResult.ok(inventoryService.aiRecognize(image));
    }

    /** 入库 */
    @RequirePermission("inventory:manage")
    @PostMapping("/items")
    public ApiResult<Long> addItem(
            @RequestParam("image") MultipartFile mainImage,
            @RequestParam String itemName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal costPrice,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) Integer stockQty) throws Exception {
        Long itemId = inventoryService.saveItem(mainImage, itemName, category,
                costPrice, salePrice, description, color, size, stockQty);
        return ApiResult.ok(itemId);
    }

    /** 查询商品列表 */
    @RequirePermission("inventory:manage")
    @GetMapping("/items")
    public ApiResult<List<ClothItem>> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(inventoryService.listItems(category, status));
    }

    /** 调整库存（+/-）*/
    @RequirePermission("inventory:manage")
    @PutMapping("/skus/{skuId}/stock")
    public ApiResult<Void> updateStock(@PathVariable Long skuId,
                                        @RequestParam int delta) {
        inventoryService.updateStock(skuId, delta);
        return ApiResult.ok();
    }

    /** 下架商品（仅管理员）*/
    @RequirePermission("user:shop_manage")
    @PutMapping("/items/{itemId}/status")
    public ApiResult<Void> setStatus(@PathVariable Long itemId,
                                      @RequestParam int status) {
        inventoryService.setItemStatus(itemId, status);
        return ApiResult.ok();
    }
}
```

**Step 3: 验证编译 & 启动**

```
mvn compile -q
mvn spring-boot:run
```

预期：启动成功，无报错

**Step 4: Commit**

```
git add -A
git commit -m "feat: add inventory service and controller with AI naming and cost price filtering"
```

---

## 阶段五：前端扩展（H5）

### Task 13：前端 - 底部导航栏

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/css/style.css`

**Step 1: 在 index.html body 末尾（script 标签前）添加底部导航**

```html
<!-- ── 底部导航 ────────────────────────────── -->
<nav class="bottom-nav" id="bottomNav">
  <a class="bnav-item active" href="index.html" data-page="home">
    <span class="bnav-icon">🏠</span>
    <span class="bnav-label">换装</span>
  </a>
  <a class="bnav-item" href="inventory.html" data-page="inventory">
    <span class="bnav-icon">📦</span>
    <span class="bnav-label">服装库</span>
  </a>
  <a class="bnav-item bnav-add" href="add-item.html" data-page="add">
    <span class="bnav-icon">＋</span>
    <span class="bnav-label">入库</span>
  </a>
  <a class="bnav-item" href="admin.html" data-page="admin" id="adminNavItem" style="display:none">
    <span class="bnav-icon">⚙️</span>
    <span class="bnav-label">管理</span>
  </a>
</nav>
```

**Step 2: 在 style.css 末尾追加底部导航样式**

```css
/* ── Bottom Navigation ─────────────────────── */
.bottom-nav {
  position: fixed; bottom: 0; left: 0; right: 0;
  display: flex; align-items: center; justify-content: space-around;
  background: var(--bg-card); border-top: 1px solid var(--border);
  padding: 8px 0 max(8px, env(safe-area-inset-bottom));
  z-index: 100;
}
.bnav-item {
  display: flex; flex-direction: column; align-items: center;
  gap: 2px; text-decoration: none; color: var(--text-3);
  font-size: 11px; min-width: 56px;
}
.bnav-item.active, .bnav-item:hover { color: var(--accent); }
.bnav-icon { font-size: 22px; line-height: 1; }
.bnav-add .bnav-icon {
  background: var(--accent); color: #fff;
  border-radius: 50%; width: 44px; height: 44px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; margin-top: -16px;
}
/* 给 main 留出底部导航空间 */
.main, .footer { padding-bottom: 72px; }
```

**Step 3: 在 main.js 登录成功回调中，根据角色显示管理入口**

```javascript
// 登录成功后调用
function onLoginSuccess(userData) {
    const roles = userData.roles || [];
    const isAdmin = roles.includes('SHOP_ADMIN') || roles.includes('SUPER_ADMIN');
    document.getElementById('adminNavItem').style.display = isAdmin ? 'flex' : 'none';
}
```

**Step 4: Commit**

```
git add -A
git commit -m "feat: add bottom navigation bar with role-based admin entry"
```

---

### Task 14：前端 - 换装结果反馈面板

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/main.js`
- Modify: `src/main/resources/static/css/style.css`

**Step 1: 在 index.html result-section 内追加反馈面板**

```html
<!-- 反馈面板（默认隐藏，不满意时展开）-->
<div class="feedback-wrap hidden" id="feedbackWrap">
  <div class="feedback-title">说说哪里不满意</div>

  <div class="fb-group">
    <div class="fb-group-label">模特问题</div>
    <div class="fb-tags">
      <label class="fb-tag"><input type="checkbox" value="model_fake"><span>模特太假</span></label>
      <label class="fb-tag"><input type="checkbox" value="pose_stiff"><span>姿势僵硬</span></label>
      <label class="fb-tag"><input type="checkbox" value="body_distorted"><span>比例失真</span></label>
      <label class="fb-tag"><input type="checkbox" value="face_blur"><span>面部模糊</span></label>
    </div>
  </div>

  <div class="fb-group">
    <div class="fb-group-label">服装问题</div>
    <div class="fb-tags">
      <label class="fb-tag"><input type="checkbox" value="cloth_unflattering"><span>不显身材</span></label>
      <label class="fb-tag"><input type="checkbox" value="color_off"><span>颜色偏差</span></label>
      <label class="fb-tag"><input type="checkbox" value="texture_blur"><span>材质模糊</span></label>
      <label class="fb-tag"><input type="checkbox" value="detail_lost"><span>细节丢失</span></label>
    </div>
  </div>

  <div class="fb-group">
    <div class="fb-group-label">场景问题</div>
    <div class="fb-tags">
      <label class="fb-tag"><input type="checkbox" value="scene_dislike"><span>场景不喜欢</span></label>
      <label class="fb-tag"><input type="checkbox" value="lighting_bad"><span>光线不自然</span></label>
      <label class="fb-tag"><input type="checkbox" value="bg_distracting"><span>背景抢眼</span></label>
    </div>
  </div>

  <div class="fb-group">
    <div class="fb-group-label">整体感觉</div>
    <div class="fb-tags">
      <label class="fb-tag"><input type="checkbox" value="style_mismatch"><span>风格不对</span></label>
      <label class="fb-tag"><input type="checkbox" value="ref_mismatch"><span>与参考差距大</span></label>
    </div>
  </div>

  <div class="fb-input-row">
    <input class="fb-input" type="text" id="fbExtraText" placeholder="补充说明（可选）">
    <button class="fb-mic" id="fbMicBtn" title="按住说话">🎤</button>
  </div>

  <button class="btn-gen" id="fbSubmitBtn" style="width:100%;margin-top:12px">
    <span class="btn-gen-star">✦</span>提交反馈 &amp; 重新生成
  </button>
</div>

<button class="btn-outline fb-trigger" id="fbTriggerBtn" style="display:none">
  不满意，说说原因
</button>
```

**Step 2: 在 main.js 追加反馈面板逻辑**

```javascript
// ── 反馈面板 ──────────────────────────────────────────────
let currentRecordId = null;

// 生图成功后存 recordId，显示反馈按钮
function onGenSuccess(data) {
    currentRecordId = data.recordId || null;
    document.getElementById('fbTriggerBtn').style.display = 'block';
    document.getElementById('feedbackWrap').classList.add('hidden');
}

document.getElementById('fbTriggerBtn')?.addEventListener('click', () => {
    document.getElementById('feedbackWrap').classList.toggle('hidden');
});

// 语音输入（Web Speech API，仅普通话）
const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
const micBtn = document.getElementById('fbMicBtn');
if (SpeechRec && micBtn) {
    const rec = new SpeechRec();
    rec.lang = 'zh-CN';
    rec.interimResults = false;
    rec.onresult = e => {
        const text = e.results[0][0].transcript;
        const input = document.getElementById('fbExtraText');
        input.value = (input.value ? input.value + ' ' : '') + text;
    };
    rec.onerror = () => showToast('语音识别失败，请重试或手动输入');
    micBtn.addEventListener('mousedown', () => { micBtn.classList.add('mic-active'); rec.start(); });
    micBtn.addEventListener('mouseup',   () => { micBtn.classList.remove('mic-active'); rec.stop(); });
} else if (micBtn) {
    micBtn.style.display = 'none'; // 浏览器不支持则隐藏
}

// 提交反馈
document.getElementById('fbSubmitBtn')?.addEventListener('click', async () => {
    if (!currentRecordId) return;
    const checked = [...document.querySelectorAll('.fb-tag input:checked')].map(i => i.value);
    const extra = document.getElementById('fbExtraText').value;

    const params = new URLSearchParams();
    params.append('recordId', currentRecordId);
    checked.forEach(t => params.append('tagCodes', t));
    if (extra) params.append('extraText', extra);

    await fetch('/tryon/feedback', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + getToken(), 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });

    // 重置反馈面板并触发重新生成
    document.querySelectorAll('.fb-tag input').forEach(i => i.checked = false);
    document.getElementById('fbExtraText').value = '';
    document.getElementById('feedbackWrap').classList.add('hidden');
    document.getElementById('genBtn').click(); // 触发重新生成
});
```

**Step 3: 在 style.css 追加反馈面板样式**

```css
/* ── Feedback Panel ───────────────────────── */
.feedback-wrap { margin-top: 20px; padding: 20px; background: var(--bg-card); border-radius: 16px; }
.feedback-title { font-size: 15px; font-weight: 600; margin-bottom: 14px; color: var(--text-1); }
.fb-group { margin-bottom: 12px; }
.fb-group-label { font-size: 12px; color: var(--text-3); margin-bottom: 6px; }
.fb-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.fb-tag input { display: none; }
.fb-tag span {
  display: block; padding: 6px 12px; border-radius: 20px;
  border: 1px solid var(--border); font-size: 13px; cursor: pointer;
  color: var(--text-2); transition: all .2s;
}
.fb-tag input:checked + span { background: var(--accent); color: #fff; border-color: var(--accent); }
.fb-input-row { display: flex; gap: 8px; margin-top: 14px; }
.fb-input { flex: 1; padding: 10px 14px; border: 1px solid var(--border); border-radius: 10px; font-size: 14px; }
.fb-mic {
  width: 42px; height: 42px; border-radius: 50%;
  border: 1px solid var(--border); background: var(--bg-page);
  font-size: 18px; cursor: pointer; flex-shrink: 0;
}
.fb-mic.mic-active { background: #fee; border-color: #f66; }
.fb-trigger { margin-top: 12px; width: 100%; }
```

**Step 4: Commit**

```
git add -A
git commit -m "feat: add feedback panel with tag selection, voice input, and preference-driven regen"
```

---

### Task 15：前端 - 拍照入库页（add-item.html）

**Files:**
- Create: `src/main/resources/static/add-item.html`

**Step 1: 创建 add-item.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" data-theme="gentle-luxury">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>StyleMe · 拍照入库</title>
  <link rel="stylesheet" href="css/style.css">
</head>
<body>
<header class="header"><div class="header-inner">
  <div class="logo"><span class="logo-mark">✦</span><span class="logo-name">拍照入库</span></div>
</div></header>

<main class="main" style="padding-top:72px">

  <!-- 拍照区域 -->
  <section class="block">
    <div class="block-label">
      <span class="block-num">01</span><span class="block-title">拍照或上传</span>
    </div>
    <div class="ucard" id="photoCard">
      <div class="uzone" id="photoZone">
        <!-- capture="environment" 调用后置摄像头 -->
        <input type="file" id="photoInput" accept="image/*" capture="environment" hidden>
        <div class="uzone-ph" id="photoPh">
          <div class="uzone-plus">📷</div>
          <span>点击拍照或选择图片</span>
        </div>
        <img class="preview-img hidden" id="previewImg" alt="预览">
      </div>
    </div>
    <button class="btn-gen" id="recognizeBtn" disabled style="margin-top:12px;width:100%">
      <span class="btn-gen-star">✦</span><span id="recognizeBtnText">AI 识别商品名称</span>
    </button>
  </section>

  <!-- 商品信息 -->
  <section class="block" id="itemFormSection" style="display:none">
    <div class="block-label">
      <span class="block-num">02</span><span class="block-title">填写商品信息</span>
    </div>
    <div class="form-stack">
      <div class="field">
        <label class="field-label">商品名称</label>
        <input class="field-input" type="text" id="itemName" placeholder="AI 已识别，可修改">
      </div>
      <div class="field">
        <label class="field-label">AI 描述</label>
        <textarea class="field-input" id="itemDesc" rows="2" placeholder="商品描述"></textarea>
      </div>
      <div class="field">
        <label class="field-label">分类</label>
        <select class="field-input" id="itemCategory">
          <option value="">请选择</option>
          <option>上装</option><option>下装</option>
          <option>外套</option><option>裙装</option><option>配饰</option>
        </select>
      </div>
      <div class="field-row">
        <div class="field" style="flex:1">
          <label class="field-label">进价（元）</label>
          <input class="field-input" type="number" id="costPrice" placeholder="选填">
        </div>
        <div class="field" style="flex:1">
          <label class="field-label">售价（元）</label>
          <input class="field-input" type="number" id="salePrice" placeholder="选填">
        </div>
      </div>
      <div class="field-row">
        <div class="field" style="flex:1">
          <label class="field-label">颜色</label>
          <input class="field-input" type="text" id="skuColor" placeholder="如：米白色">
        </div>
        <div class="field" style="flex:1">
          <label class="field-label">尺码</label>
          <select class="field-input" id="skuSize">
            <option value="">请选择</option>
            <option>XS</option><option>S</option><option>M</option>
            <option>L</option><option>XL</option><option>XXL</option>
          </select>
        </div>
        <div class="field" style="flex:1">
          <label class="field-label">库存数</label>
          <input class="field-input" type="number" id="stockQty" placeholder="0" value="1">
        </div>
      </div>
    </div>
    <button class="btn-gen" id="saveBtn" style="width:100%;margin-top:16px">
      <span class="btn-gen-star">✦</span>确认入库
    </button>
  </section>

  <div class="toast hidden" id="toast"><span class="toast-icon">⚠</span><span id="toastMsg"></span></div>
</main>

<!-- 底部导航（复用）-->
<nav class="bottom-nav">
  <a class="bnav-item" href="index.html"><span class="bnav-icon">🏠</span><span class="bnav-label">换装</span></a>
  <a class="bnav-item" href="inventory.html"><span class="bnav-icon">📦</span><span class="bnav-label">服装库</span></a>
  <a class="bnav-item bnav-add active" href="add-item.html"><span class="bnav-icon">＋</span><span class="bnav-label">入库</span></a>
</nav>

<script>
const token = () => localStorage.getItem('token') || '';
const toast = (msg) => {
    document.getElementById('toastMsg').textContent = msg;
    const t = document.getElementById('toast');
    t.classList.remove('hidden');
    setTimeout(() => t.classList.add('hidden'), 3000);
};

let photoFile = null;

document.getElementById('photoZone').addEventListener('click', () =>
    document.getElementById('photoInput').click());

document.getElementById('photoInput').addEventListener('change', e => {
    photoFile = e.target.files[0];
    if (!photoFile) return;
    const reader = new FileReader();
    reader.onload = ev => {
        document.getElementById('previewImg').src = ev.target.result;
        document.getElementById('previewImg').classList.remove('hidden');
        document.getElementById('photoPh').classList.add('hidden');
        document.getElementById('recognizeBtn').disabled = false;
    };
    reader.readAsDataURL(photoFile);
});

document.getElementById('recognizeBtn').addEventListener('click', async () => {
    if (!photoFile) return;
    document.getElementById('recognizeBtnText').textContent = '识别中…';
    document.getElementById('recognizeBtn').disabled = true;

    const fd = new FormData();
    fd.append('image', photoFile);
    const res = await fetch('/inventory/recognize', {
        method: 'POST', headers: { 'Authorization': 'Bearer ' + token() }, body: fd
    });
    const data = await res.json();

    if (data.code === 200) {
        document.getElementById('itemName').value = data.data.name || '';
        document.getElementById('itemDesc').value  = data.data.description || '';
        document.getElementById('itemFormSection').style.display = 'block';
        document.getElementById('itemFormSection').scrollIntoView({ behavior: 'smooth' });
    } else {
        toast('AI识别失败，请手动填写');
        document.getElementById('itemFormSection').style.display = 'block';
    }
    document.getElementById('recognizeBtnText').textContent = 'AI 识别商品名称';
    document.getElementById('recognizeBtn').disabled = false;
});

document.getElementById('saveBtn').addEventListener('click', async () => {
    const itemName = document.getElementById('itemName').value.trim();
    if (!itemName) { toast('请填写商品名称'); return; }
    if (!photoFile) { toast('请先拍照或上传图片'); return; }

    document.getElementById('saveBtn').textContent = '入库中…';

    const fd = new FormData();
    fd.append('image', photoFile);
    fd.append('itemName', itemName);
    ['itemCategory','costPrice','salePrice','skuColor','skuSize','stockQty'].forEach(id => {
        const val = document.getElementById(id)?.value;
        if (val) fd.append({
            itemCategory:'category', costPrice:'costPrice', salePrice:'salePrice',
            skuColor:'color', skuSize:'size', stockQty:'stockQty'
        }[id], val);
    });
    const desc = document.getElementById('itemDesc').value;
    if (desc) fd.append('description', desc);

    const res = await fetch('/inventory/items', {
        method: 'POST', headers: { 'Authorization': 'Bearer ' + token() }, body: fd
    });
    const data = await res.json();
    if (data.code === 200) {
        toast('入库成功！');
        setTimeout(() => location.href = 'inventory.html', 1200);
    } else {
        toast('入库失败：' + (data.msg || '请重试'));
    }
    document.getElementById('saveBtn').textContent = '确认入库';
});
</script>
</body>
</html>
```

**Step 2: 在 style.css 追加表单样式**

```css
/* ── Form Stack ──────────────────────────── */
.form-stack { display: flex; flex-direction: column; gap: 14px; }
.field-row { display: flex; gap: 10px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 13px; color: var(--text-2); font-weight: 500; }
textarea.field-input { resize: vertical; min-height: 60px; }
.preview-img { width: 100%; border-radius: 12px; object-fit: cover; max-height: 300px; }
```

**Step 3: Commit**

```
git add -A
git commit -m "feat: add photo capture and AI-naming inventory page"
```

---

### Task 16：前端 - 服装库列表页（inventory.html）

**Files:**
- Create: `src/main/resources/static/inventory.html`

**Step 1: 创建简洁的商品列表页（含分类筛选、进价权限判断）**

核心结构：
- 顶部分类 Tab（全部/上装/下装/外套/裙装/配饰）
- 商品卡片列表（主图 + 名称 + 售价 + 进价条件显示）
- 「用此衣换装」快捷按钮跳转 index.html 并预填图片

详细代码参考 add-item.html 风格，调用 `GET /inventory/items?category=xxx` 接口渲染。

**Step 2: Commit**

```
git add -A
git commit -m "feat: add inventory list page with category filter and cost price guard"
```

---

## 阶段六：收尾验证

### Task 17：全流程端到端验证

**Step 1: 启动服务**

```
mvn spring-boot:run
```

**Step 2: 验证清单**

| 场景 | 预期结果 |
|------|----------|
| admin 登录，调 `GET /admin/shops` | 返回店铺列表 |
| 创建店铺管理员账号，设置 daily_quota=5 | 第6次生图返回429提示 |
| 换装生图成功，提交反馈 model_fake+pose_stiff | `user_preference.tag_weights` 更新 |
| 再次生图，查看 `tryon_record.prompt_used` | 包含「用户偏好优化」前缀 |
| SHOP_USER(can_see_cost=0) 查询商品列表 | cost_price 字段为 null |
| SHOP_ADMIN 查询商品列表 | cost_price 正常显示 |
| 拍照入库流程：拍照→AI识别→填价格→确认 | 商品出现在服装库列表 |
| `GET /admin/usage/shops` | 返回各店铺 Token 汇总 |

**Step 3: 最终提交**

```
git add -A
git commit -m "feat: StyleMe SaaS v2.0 complete - auth/tryon/inventory modules"
```

---

## 附录：新增 API 汇总

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /admin/shops | sys:manage | 查所有店铺 |
| POST | /admin/shops | sys:manage | 创建店铺 |
| GET | /admin/shop/users | user:shop_manage | 查本店用户 |
| POST | /admin/shop/users | user:shop_manage | 创建子账号 |
| PUT | /admin/shop/users/{id} | user:shop_manage | 修改配额/进价权限 |
| GET | /admin/usage/shops | sys:manage | 用量看板-店铺汇总 |
| GET | /admin/usage/shops/{id}/users | sys:manage | 用量看板-用户明细 |
| POST | /tryon/recommend | image:recommend | 换装生图（含配额+偏好）|
| POST | /tryon/feedback | tryon:feedback | 提交反馈 |
| POST | /inventory/recognize | inventory:manage | AI识别商品名称 |
| POST | /inventory/items | inventory:manage | 入库 |
| GET | /inventory/items | inventory:manage | 查商品列表 |
| PUT | /inventory/skus/{id}/stock | inventory:manage | 调整库存 |
| PUT | /inventory/items/{id}/status | user:shop_manage | 上下架商品 |
