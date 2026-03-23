-- ============================================================
-- StyleMe SaaS v2.0 数据库迁移脚本（兼容 MySQL 5.6+）
-- ============================================================

-- ── 辅助存储过程：安全新增列（替代 MySQL 8 的 ADD COLUMN IF NOT EXISTS）──
DROP PROCEDURE IF EXISTS _add_col;
DELIMITER //
CREATE PROCEDURE _add_col(IN tbl VARCHAR(64), IN col VARCHAR(64), IN def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @_sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', def);
        PREPARE _stmt FROM @_sql;
        EXECUTE _stmt;
        DEALLOCATE PREPARE _stmt;
    END IF;
END //
DELIMITER ;

-- 扩展：sys_user 新增字段
CALL _add_col('sys_user', 'shop_id',          'BIGINT  DEFAULT NULL    COMMENT ''所属店铺ID，超管为NULL''');
CALL _add_col('sys_user', 'daily_quota',      'INT     DEFAULT -1      COMMENT ''每日生图次数上限，-1不限''');
CALL _add_col('sys_user', 'used_today',       'INT     DEFAULT 0       COMMENT ''今日已使用次数''');
CALL _add_col('sys_user', 'quota_date',       'DATE    DEFAULT NULL    COMMENT ''次数统计日期，用于每日重置''');
CALL _add_col('sys_user', 'can_see_cost',     'TINYINT DEFAULT 0       COMMENT ''是否可查看进价 1是 0否''');
CALL _add_col('sys_user', 'total_token_used', 'BIGINT  DEFAULT 0       COMMENT ''累计消耗Token数''');

-- 扩展：sys_shop 新增 shop_no 字段（兼容已建表的情况）
CALL _add_col('sys_shop', 'shop_no', 'VARCHAR(24) DEFAULT NULL COMMENT ''店铺编号，格式：SHOPyyyyMMddHHmmss+3位序号'' AFTER id');

-- tryon_record 新增计时字段 + 状态说明（0=失败 1=成功 2=生成中）
CALL _add_col('tryon_record', 'upload_second',   'VARCHAR(10) DEFAULT NULL COMMENT ''上传耗时(s)''');
CALL _add_col('tryon_record', 'generate_second', 'VARCHAR(10) DEFAULT NULL COMMENT ''生图耗时(s)''');
CALL _add_col('tryon_record', 'total_second',    'VARCHAR(10) DEFAULT NULL COMMENT ''总耗时(s)''');

-- cloth_image.tos_url 允许为空（异步上传时先插占位记录）
-- 使用存储过程检查列类型避免重复修改
DROP PROCEDURE IF EXISTS _mod_col_nullable;
DELIMITER //
CREATE PROCEDURE _mod_col_nullable(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
    DECLARE nullable_val VARCHAR(3);
    SELECT IS_NULLABLE INTO nullable_val
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col;
    IF nullable_val = 'NO' THEN
        SET @_sql = CONCAT('ALTER TABLE `', tbl, '` MODIFY COLUMN `', col, '` VARCHAR(500) NULL');
        PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;
    END IF;
END //
DELIMITER ;
CALL _mod_col_nullable('cloth_image', 'tos_url');
DROP PROCEDURE IF EXISTS _mod_col_nullable;

-- 清理辅助存储过程
DROP PROCEDURE IF EXISTS _add_col;

-- 新增：店铺表
CREATE TABLE IF NOT EXISTS sys_shop (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_no     VARCHAR(24) DEFAULT NULL COMMENT '店铺编号，格式：SHOPyyyyMMddHHmmss+3位序号',
    shop_name   VARCHAR(100) NOT NULL COMMENT '店铺名称',
    status      TINYINT DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

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
    item_no      VARCHAR(20)   DEFAULT NULL COMMENT '商品编号，yyyyMMddHHmmss+3位序号',
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

-- 新增角色
INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
    (3, 'SUPER_ADMIN', '超级管理员'),
    (4, 'SHOP_ADMIN',  '店铺管理员'),
    (5, 'SHOP_USER',   '店铺子账号');

-- 新增权限
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, url, method) VALUES
    (4, 'inventory:manage', '管理服装库存',  '/inventory/**',       NULL),
    (5, 'inventory:cost',   '查看进价',      NULL,                  NULL),
    (6, 'user:shop_manage', '管理本店用户',  '/auth/shop/**',       NULL),
    (7, 'sys:manage',       '平台级管理',    '/admin/**',           NULL),
    (8, 'tryon:feedback',   '提交换装反馈',  '/tryon/feedback',     'POST');

-- 超级管理员拥有所有权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
    SELECT 3, id FROM sys_permission;

-- 店铺管理员权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES
    (4,1),(4,2),(4,4),(4,5),(4,6),(4,8);

-- 店铺子账号权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES
    (5,1),(5,2),(5,4),(5,8);

-- 初始超级管理员（密码 admin123，同 BCrypt hash）
INSERT IGNORE INTO sys_user (id, username, password, status) VALUES
    (2, 'superadmin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (2, 3);

-- 默认测试店铺
INSERT IGNORE INTO sys_shop (id, shop_name, status) VALUES
    (1, '默认测试店铺', 1);

-- 默认店铺管理员（密码 admin123，绑定测试店铺，可查看进价）
INSERT IGNORE INTO sys_user (id, username, password, status, shop_id, daily_quota, can_see_cost) VALUES
    (3, 'shop_admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1, 1, -1, 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (3, 4);

-- 默认店铺子账号（密码 admin123，每日限 10 次，不可查看进价）
INSERT IGNORE INTO sys_user (id, username, password, status, shop_id, daily_quota, can_see_cost) VALUES
    (4, 'shop_user', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1, 1, 10, 0);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (4, 5);

-- ────────────────────────────────────────────
-- 销售订单表
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cloth_order (
    id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    shop_id       BIGINT        NOT NULL COMMENT '所属店铺',
    order_no      VARCHAR(24)   NOT NULL UNIQUE COMMENT '单号',
    order_type    TINYINT       NOT NULL COMMENT '1=销售 2=退货',
    total_amount  DECIMAL(10,2) DEFAULT 0,
    remark        VARCHAR(200)  DEFAULT NULL,
    orig_order_id BIGINT        DEFAULT NULL COMMENT '退货时关联原销售单ID',
    operator_id   BIGINT        DEFAULT NULL,
    created_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shop_time (shop_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单';

CREATE TABLE IF NOT EXISTS cloth_order_item (
    id         BIGINT        PRIMARY KEY AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    shop_id    BIGINT        DEFAULT NULL COMMENT '所属店铺',
    item_id    BIGINT        NOT NULL,
    sku_id     BIGINT        NOT NULL,
    item_name  VARCHAR(100)  DEFAULT NULL COMMENT '商品名快照',
    sku_desc   VARCHAR(100)  DEFAULT NULL COMMENT 'SKU规格快照',
    qty        INT           NOT NULL,
    unit_price DECIMAL(10,2) DEFAULT NULL COMMENT '实际售价快照',
    cost_price DECIMAL(10,2) DEFAULT NULL COMMENT '进价快照',
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 兼容已存在表：给 cloth_order_item 补齐 shop_id 列
CALL _add_col('cloth_order_item', 'shop_id', 'BIGINT DEFAULT NULL COMMENT ''所属店铺''');

-- 回填历史数据：shop_id = cloth_order.shop_id
UPDATE cloth_order_item toi
JOIN cloth_order o ON toi.order_id = o.id
SET toi.shop_id = o.shop_id
WHERE toi.shop_id IS NULL;

-- sys_user 新增 AI 生图权限字段
CALL _add_col('sys_user', 'can_use_ai', 'TINYINT DEFAULT 1 COMMENT ''是否可用AI生图：1=是 0=否''');
