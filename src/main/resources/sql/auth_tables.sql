-- ============================================================
-- RBAC 登录模块建表脚本（数据库：gen_cloth_ai）
-- 密码使用 BCrypt 加密，初始管理员密码：admin123
-- ============================================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录用户名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    phone       VARCHAR(20)  DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码，如 ADMIN / USER',
    role_name   VARCHAR(50) NOT NULL COMMENT '角色名称',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT     NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 3. 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    perm_code   VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码，如 image:generate',
    perm_name   VARCHAR(50)  NOT NULL COMMENT '权限名称',
    url         VARCHAR(200) DEFAULT NULL COMMENT '接口路径（含通配符），如 /TestController/**',
    method      VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP 方法，NULL 表示不限',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 4. 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 5. 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    perm_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, perm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 角色
INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES
    (1, 'ADMIN', '管理员'),
    (2, 'USER',  '普通用户');

-- 权限
INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, url, method) VALUES
    (1, 'image:generate', '生成试衣图',   '/TestController/testDyDemo', 'POST'),
    (2, 'image:recommend','穿搭推荐',     '/TestController/recommend',  'POST'),
    (3, 'user:manage',    '用户管理',     '/auth/register',             'POST');

-- 管理员拥有所有权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES (1, 1), (1, 2), (1, 3);
-- 普通用户只有生图/推荐权限
INSERT IGNORE INTO sys_role_permission (role_id, perm_id) VALUES (2, 1), (2, 2);

-- 初始管理员（密码：admin123，BCrypt 加密）
-- 若需重新生成：cn.hutool.crypto.BCrypt.hashpw("admin123", cn.hutool.crypto.BCrypt.gensalt())
INSERT IGNORE INTO sys_user (id, username, password, status) VALUES
    (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1);

-- 管理员绑定 ADMIN 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
