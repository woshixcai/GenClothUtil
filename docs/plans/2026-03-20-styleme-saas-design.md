# StyleMe SaaS 扩展设计文档

**日期：** 2026-03-20  
**项目：** GenClothUtil / StyleMe · 智能换装  
**版本：** v2.0 设计稿  
**状态：** 已确认，待实施

---

## 一、背景与目标

现有 StyleMe 已具备基础换装能力（火山引擎 ARK 生图、TOS 上传、JWT 鉴权、RBAC 权限）。  
本次扩展目标是将其升级为面向服装商家的 **SaaS 平台**，包含三大模块：

1. **三级权限体系** — 超级管理员 → 店铺管理员 → 子账号，含用量看板
2. **换装反馈 + 偏好学习** — 记录不满意原因，建立用户偏好权重，下次生图自动优化 prompt
3. **服装进销存 + 拍照入库** — 拍照 AI 取名，进价/售价管理，SKU 库存，权限隔离

前端：H5 先行（复用现有 Spring Boot 静态资源），后续再包微信小程序壳，共用同一套后端 API。

---

## 二、架构方案：模块化单体

在现有 Spring Boot 基础上按业务领域重组包结构，清晰划分模块边界，单一部署单元。

```
com.UiUtil
├── auth/                  # 认证 & 权限（现有扩展）
│   ├── controller/        # AuthController + UserMgmtController（新）
│   ├── service/
│   ├── mapper/
│   └── entity/            # SysUser / SysRole / SysShop（新）
│
├── tryon/                 # 换装 + 反馈 + 偏好学习（新模块）
│   ├── controller/
│   ├── service/
│   ├── mapper/
│   └── entity/            # TryonRecord / TryonFeedback / UserPreference
│
├── inventory/             # 服装进销存（全新模块）
│   ├── controller/
│   ├── service/
│   ├── mapper/
│   └── entity/            # ClothItem / ClothImage / ClothSku
│
└── shared/                # 公共（现有 util/result/annotation/interceptor 迁入）
    ├── annotation/
    ├── interceptor/
    ├── context/
    ├── result/
    └── util/
```

---

## 三、模块一：三级权限体系

### 3.1 账户层级

```
超级管理员 (SUPER_ADMIN)          shop_id = NULL
│  ├── 创建/禁用店铺账号
│  ├── 查看所有店铺数据
│  └── 用量看板（店铺 → 账号钻取）
│
店铺管理员 (SHOP_ADMIN)           shop_id = 所属店铺
│  ├── 创建/禁用本店子账号
│  ├── 设置子账号每日生图次数配额
│  ├── 控制子账号能否查看进价
│  └── 查看本店所有数据
│
子账号 (SHOP_USER)                shop_id = 所属店铺
   ├── 使用换装功能（受次数配额限制）
   ├── 管理服装库存（进价按权限显隐）
   └── 只能看自己操作的数据
```

### 3.2 数据库变更

```sql
-- 新增：店铺表
CREATE TABLE sys_shop (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_name   VARCHAR(100) NOT NULL,
    status      TINYINT DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 扩展：sys_user 新增字段
ALTER TABLE sys_user
    ADD COLUMN shop_id       BIGINT  DEFAULT NULL  COMMENT '所属店铺，超管为NULL',
    ADD COLUMN daily_quota   INT     DEFAULT -1     COMMENT '每日生图次数上限，-1不限',
    ADD COLUMN used_today    INT     DEFAULT 0      COMMENT '今日已使用次数',
    ADD COLUMN quota_date    DATE    DEFAULT NULL   COMMENT '次数统计日期，用于每日重置',
    ADD COLUMN can_see_cost  TINYINT DEFAULT 0      COMMENT '是否可查看进价 1是 0否',
    ADD COLUMN total_token_used BIGINT DEFAULT 0   COMMENT '累计消耗Token数';

-- 新增：调用流水表（用量看板数据源）
CREATE TABLE usage_log (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    shop_id      BIGINT      NOT NULL,
    action       VARCHAR(50) COMMENT '操作类型：tryon / recommend / ai_name',
    token_used   INT         DEFAULT 0,
    created_time DATETIME    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新增权限编码（插入 sys_permission）
-- inventory:manage   管理服装库存
-- inventory:cost     查看进价
-- user:shop_manage   管理本店用户（SHOP_ADMIN）
-- sys:manage         平台级管理（SUPER_ADMIN）
```

### 3.3 每日配额重置逻辑

每次调用换装 API 时：
1. 检查 `quota_date` 是否等于今天，不等则重置 `used_today = 0`，更新 `quota_date`
2. 若 `daily_quota != -1` 且 `used_today >= daily_quota`，拒绝请求，返回 429
3. 调用成功后 `used_today + 1`，`total_token_used += token_used`

### 3.4 用量看板 API

| 接口 | 说明 |
|------|------|
| `GET /admin/usage/shops` | 各店铺汇总（当日/当月/累计 Token，生图次数）|
| `GET /admin/usage/shops/{shopId}/users` | 某店铺内各账号明细 |

### 3.5 前端管理入口

- **SUPER_ADMIN**：顶部导航显示「平台管理」→ 店铺列表 + 用量看板
- **SHOP_ADMIN**：显示「店铺管理」→ 子账号列表（含配额设置、进价权限开关）
- **SHOP_USER**：无管理入口

---

## 四、模块二：换装反馈 + 偏好学习

### 4.1 业务流程

```
用户查看换装结果
    ↓
[满意] → 保存 / 结束
[不满意，说说原因] → 弹出反馈面板
    ↓
勾选标签（多选）+ 文字/语音补充
    ↓
提交 → 后台记录反馈 → 更新偏好权重 → 触发重新生成（带偏好 prompt）
```

### 4.2 不满意标签体系

| 分类 | 标签名称 | tag_code |
|------|----------|----------|
| 模特问题 | 模特太假/像AI合成 | `model_fake` |
| 模特问题 | 姿势太僵硬 | `pose_stiff` |
| 模特问题 | 比例失真/身材变形 | `body_distorted` |
| 模特问题 | 面部模糊/不自然 | `face_blur` |
| 服装问题 | 衣服不显身材 | `cloth_unflattering` |
| 服装问题 | 颜色/色调偏差 | `color_off` |
| 服装问题 | 材质/纹理模糊 | `texture_blur` |
| 服装问题 | 衣服细节丢失（纽扣/图案）| `detail_lost` |
| 场景问题 | 场景/背景不喜欢 | `scene_dislike` |
| 场景问题 | 光线不自然 | `lighting_bad` |
| 场景问题 | 背景太抢眼 | `bg_distracting` |
| 整体感觉 | 整体风格不对 | `style_mismatch` |
| 整体感觉 | 与参考图差距太大 | `ref_mismatch` |

### 4.3 数据库设计

```sql
-- 生图记录主表
CREATE TABLE tryon_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    shop_id       BIGINT       NOT NULL,
    result_urls   TEXT         COMMENT 'JSON数组，生成图片的TOS URL',
    prompt_used   TEXT         COMMENT '本次实际发送给模型的完整prompt',
    pref_snapshot TEXT         COMMENT '本次带入的偏好快照（JSON）',
    style         VARCHAR(20),
    scene         VARCHAR(20),
    season        VARCHAR(20),
    token_used    INT          DEFAULT 0,
    status        TINYINT      DEFAULT 1 COMMENT '1正常 0已删除',
    created_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    is_deleted    TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 反馈表
CREATE TABLE tryon_feedback (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id    BIGINT       NOT NULL COMMENT '关联 tryon_record.id',
    user_id      BIGINT       NOT NULL,
    tag_codes    VARCHAR(500) COMMENT '不满意标签，逗号分隔，如 model_fake,pose_stiff',
    extra_text   VARCHAR(500) COMMENT '用户补充文字（文字输入或语音转文字）',
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户偏好权重表（每用户一行）
CREATE TABLE user_preference (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL UNIQUE,
    tag_weights  TEXT   COMMENT 'JSON对象：{tag_code: 累计负向反馈次数}',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.4 偏好 → Prompt 注入机制（第一期：标签聚合）

```
读取 user_preference.tag_weights
    ↓
按权重降序取 Top 3 tag_code
    ↓
映射为中文负向引导语句，拼入 prompt 头部

示例输出：
"【用户偏好优化】该用户历史反馈：
 - 模特看起来像AI合成（5次），请确保皮肤纹理自然真实；
 - 姿势太僵硬（3次），请生成自然放松的站姿；
 - 背景不喜欢（2次），请使用简洁干净的纯色背景。
 以上为优先改善方向，其余要求同下："
```

第二期（规划中）：对不满意图片提取嵌入向量，存入向量数据库（pgvector/Milvus），生图前做相似度检索排除。

### 4.5 语音输入

- 使用浏览器原生 **Web Speech API**（`SpeechRecognition`，`lang: zh-CN`，仅支持普通话）
- 零成本，无需后端接口，主流手机 Chrome/Safari 支持
- 降级：浏览器不支持时隐藏语音按钮，仅保留文字输入框
- 识别结果**追加**（不覆盖）到文字输入框，用户可继续编辑
- 语音文本与手打文本统一存入 `extra_text` 字段，后端无感知

### 4.6 前端交互

换装结果页底部新增「不满意，说说原因」按钮，点击展开反馈面板：
- 标签区域（按分类分组，多选）
- 文字输入框 + [🎤 按住说话] 按钮
- [提交反馈 & 重新生成] — 加载提示「正在结合你的偏好优化...」

---

## 五、模块三：服装进销存 + 拍照入库

### 5.1 核心业务流程

```
拍照 / 上传图片（手机摄像头或相册）
    ↓
调用 AliyunUtils（Dashscope 视觉模型）AI 识别
    → 建议商品名称（颜色+款式，≤10字）
    → 生成商品描述（≤50字）
    ↓
用户确认/修改名称 + 填写：
    进价 / 售价 / 分类 / 颜色 / 尺码 / 库存数量
    ↓
确认入库 → 图片上传 TOS → 写入 DB
    ↓
进入商品列表，支持查询/调整库存/下架
```

### 5.2 数据库设计

```sql
-- 商品主表
CREATE TABLE cloth_item (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id      BIGINT        NOT NULL,
    item_name    VARCHAR(100)  NOT NULL COMMENT 'AI取名或用户确认后的名称',
    category     VARCHAR(50)   COMMENT '分类：上装/下装/外套/裙装/配饰等',
    cost_price   DECIMAL(10,2) DEFAULT NULL COMMENT '进价（权限控制可见性）',
    sale_price   DECIMAL(10,2) DEFAULT NULL COMMENT '售价',
    description  VARCHAR(500)  COMMENT 'AI生成的商品描述',
    status       TINYINT       DEFAULT 1 COMMENT '1上架 0下架',
    created_by   BIGINT        COMMENT '入库操作人 user_id',
    created_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT       DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品图片表（一商品多图）
CREATE TABLE cloth_image (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id      BIGINT       NOT NULL,
    tos_url      VARCHAR(500) NOT NULL,
    is_main      TINYINT      DEFAULT 0 COMMENT '1为主图',
    sort_order   INT          DEFAULT 0,
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SKU表（颜色+尺码维度库存）
CREATE TABLE cloth_sku (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id      BIGINT      NOT NULL,
    color        VARCHAR(30) COMMENT '颜色',
    size         VARCHAR(10) COMMENT '尺码：XS/S/M/L/XL/XXL',
    stock_qty    INT         DEFAULT 0,
    updated_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.3 AI 取名 Prompt

```
"请识别这件服装，用10字以内给出一个简洁的商品名称，格式为「颜色+款式」，
 例如「米白色宽松休闲卫衣」。同时用一句话（50字以内）描述这件衣服的特点。
 按以下格式返回，不要其他内容：
 名称：xxx
 描述：xxx"
```

### 5.4 权限控制

| 功能 | SUPER_ADMIN | SHOP_ADMIN | SHOP_USER（can_see_cost=1） | SHOP_USER（can_see_cost=0） |
|------|:-----------:|:----------:|:---------------------------:|:---------------------------:|
| 查看进价 | ✅ | ✅ | ✅ | ❌（返回null） |
| 入库 / 编辑商品 | ✅ | ✅ | ✅ | ✅ |
| 修改进价 | ✅ | ✅ | ❌ | ❌ |
| 下架 / 删除商品 | ✅ | ✅ | ❌ | ❌ |

**后端强制过滤**：API 响应时检查当前用户 `can_see_cost`，为 0 则将 `cost_price` 字段置为 `null` 再返回，前端不渲染进价列。数据隔离靠 SQL 中 `WHERE shop_id = #{currentUser.shopId}` 强制过滤。

### 5.5 前端页面结构（H5 新增）

```
底部导航栏（新增）
├── 🏠 换装          → 现有 index.html
├── 📦 服装库         → 商品列表页（搜索/分类筛选/状态筛选）
│    └── 商品详情页   → 多图展示 / SKU库存 / 进价（权限判断）/ 「用此衣换装」快捷按钮
├── ➕ 拍照入库       → 拍照→AI识别→填价格→确认
└── ⚙️ 管理（按角色显示）
     ├── 子账号管理   → SHOP_ADMIN：账号列表/配额/进价权限
     ├── 店铺管理     → SUPER_ADMIN：店铺列表/开关
     └── 用量看板     → SUPER_ADMIN：店铺汇总 → 账号钻取
```

---

## 六、实施分期规划

### 第一期（核心功能）
1. 包结构重组（shared 模块抽取，现有代码迁移）
2. 三级权限体系（sys_shop 表、用户扩展字段、配额逻辑）
3. 用量看板后端 API + 前端展示
4. 换装反馈标签系统（DB + 后端 + 前端面板 + 语音输入）
5. 偏好权重计算 + prompt 注入
6. 服装进销存（商品/图片/SKU 的 CRUD）
7. 拍照入库 + AI 取名
8. 进价权限隔离
9. 前端底部导航 + 新增页面

### 第二期（升级优化）
- 偏好学习升级：图片嵌入向量 → 向量数据库（pgvector）
- 微信小程序壳（复用同一套后端 API）
- 进销存报表（销售数据、库存预警）

---

## 七、技术栈补充

| 组件 | 用途 |
|------|------|
| Spring Boot（现有）| 后端框架 |
| MyBatis Plus（现有）| ORM |
| MySQL（现有）| 主数据库 |
| 火山引擎 ARK（现有）| 换装生图 |
| 阿里云 Dashscope（现有）| AI取名视觉识别 |
| 火山引擎 TOS（现有）| 图片存储 |
| Web Speech API（新增）| 语音转文字（浏览器原生）|
| pgvector（第二期）| 图片偏好向量存储 |
