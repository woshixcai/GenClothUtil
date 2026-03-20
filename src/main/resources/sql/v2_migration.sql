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

-- 扩展：sys_user 新增字段（使用 IF NOT EXISTS 兼容重复执行）
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
