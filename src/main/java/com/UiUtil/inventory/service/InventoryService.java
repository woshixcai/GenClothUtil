package com.UiUtil.inventory.service;

/**
 * 库存核心服务：商品入库（含异步 TOS 上传）、AI 图片识别命名、进货小票 AI 解析与批量导入，
 * 以及商品列表查询（含主图 URL 回填）。
 */
import com.UiUtil.inventory.entity.*;
import com.UiUtil.inventory.mapper.*;
import com.UiUtil.shared.context.UserContext;
import com.UiUtil.shared.util.AliyunUtils;
import com.UiUtil.shared.util.ImageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.UiUtil.auth.service.UsageLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.PostConstruct;

import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    @Autowired AliyunUtils             aliyunUtils;
    @Autowired ImageUtils              imageUtils;
    @Autowired ClothItemMapper         itemMapper;
    @Autowired ClothImageMapper        imageMapper;
    @Autowired ClothSkuMapper          skuMapper;
    @Autowired AsyncImageUploadService asyncUpload;
    @Autowired UsageLogService usageLogService;

    // 商品编号生成：yyyyMMddHHmmss + 3位序号，同一秒内最多999个
    private static final AtomicInteger SEQ      = new AtomicInteger(0);
    private static volatile String     SEQ_MARK = "";

    private static String genItemNo() {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        synchronized (InventoryService.class) {
            if (!ts.equals(SEQ_MARK)) { SEQ_MARK = ts; SEQ.set(0); }
            return ts + String.format("%03d", SEQ.incrementAndGet());
        }
    }

    // 外置 prompt（便于你后续按商陆花开小票版式不断调参）
    @Value("classpath:prompts/inventory_item_name_prompt.txt")
    private Resource itemNamePromptResource;

    @Value("classpath:prompts/inventory_receipt_parse_prompt.txt")
    private Resource receiptParsePromptResource;

    private String itemNamePrompt;
    @SuppressWarnings("unused")
    private String receiptParsePrompt;

    // 兜底：防止资源文件缺失导致直接不可用（建议你上线前确保文件存在）
    private static final String AI_NAME_PROMPT_FALLBACK =
            "识别图中服装/配饰，严格按格式返回，不输出其他内容：\n" +
            "名称：(颜色+款式，10字内)\n" +
            "描述：(版型/材质/风格，30字内)\n" +
            "分类：(上装/下装/外套/裙装/配饰 选一个)";

    private static final String RECEIPT_PARSE_PROMPT_FALLBACK =
            "把进货小票解析成严格 JSON：{orderNo:\"\", items:[{lineNo:1,itemName:\"\",category:\"\",color:\"\",size:\"\",qty:0,unitCostPrice:null}]}。只输出 JSON。";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryService.class);

    /**
     * AI 识别商品名称/描述/分类，纯识别，不涉及 TOS 上传。
     */
    public Map<String, String> aiRecognize(MultipartFile image) throws Exception {
        long t0 = System.currentTimeMillis();
        String raw;
        int tokenUsed = 0;
        try {
            AliyunUtils.QwenTextUsageResult r = aliyunUtils.qWenVLPlusWithUsage(image, itemNamePrompt != null ? itemNamePrompt : AI_NAME_PROMPT_FALLBACK);
            raw = r.getText();
            tokenUsed = r.getTokenUsed() == null ? 0 : r.getTokenUsed();
            // 记录使用量：用于超管的按店铺/按月统计
            usageLogService.record("ai_name", tokenUsed);
            log.info("[AI识别] 耗时 {}ms，图片大小 {}KB，返回：{}",
                    System.currentTimeMillis() - t0,
                    image.getSize() / 1024,
                    raw.replace("\n", " | "));
        } catch (Exception e) {
            log.error("[AI识别] 耗时 {}ms 后失败，图片大小 {}KB，错误：{}",
                    System.currentTimeMillis() - t0,
                    image.getSize() / 1024,
                    e.getMessage());
            throw e;
        }
        Map<String, String> result = new HashMap<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("名称：") || trimmed.startsWith("名称:")) {
                result.put("name", trimmed.replaceFirst("名称[：:]\\s*", "").trim());
            } else if (trimmed.startsWith("描述：") || trimmed.startsWith("描述:")) {
                result.put("description", trimmed.replaceFirst("描述[：:]\\s*", "").trim());
            } else if (trimmed.startsWith("分类：") || trimmed.startsWith("分类:")) {
                result.put("category", trimmed.replaceFirst("分类[：:]\\s*", "").trim());
            }
        }
        if (!result.containsKey("name"))        result.put("name", "未识别商品");
        if (!result.containsKey("description")) result.put("description", "");
        if (!result.containsKey("category"))    result.put("category", "");
        return result;
    }

    @PostConstruct
    public void loadPrompts() {
        this.itemNamePrompt = loadTextResource(itemNamePromptResource);
        this.receiptParsePrompt = loadTextResource(receiptParsePromptResource);
        log.info("[Prompt] itemNamePrompt loaded={}, receiptParsePrompt loaded={}",
                itemNamePrompt != null, receiptParsePrompt != null);
    }

    private static String loadTextResource(Resource resource) {
        if (resource == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 写商品记录，图片异步上传到 TOS，入库立即返回。
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

        Long finalShopId = user.getShopId();
        if (finalShopId == null) {
            throw new RuntimeException("当前账号未绑定店铺，无法入库，请联系超级管理员");
        }

        ClothItem item = new ClothItem();
        item.setShopId(finalShopId);
        item.setItemNo(genItemNo());
        item.setItemName(itemName);
        item.setCategory(category);
        item.setCostPrice(costPrice);
        item.setSalePrice(salePrice);
        item.setDescription(description);
        item.setStatus(1);
        item.setCreatedBy(user.getUserId());
        item.setCreatedTime(new Date());
        itemMapper.insert(item);

        if (mainImage != null && !mainImage.isEmpty()) {
            byte[] bytes    = mainImage.getBytes();
            String filename = mainImage.getOriginalFilename();
            asyncUpload.uploadAndLink(item.getId(), bytes, filename);
        }

        // 颜色：未填时从商品名称中匹配关键词；尺码：默认均码。每条入库商品固定生成一条 SKU。
        String c = normStr(color);
        if (c == null) {
            c = extractColorFromText(itemName);
        }
        String sz = normStr(size);
        if (sz == null) {
            sz = DEFAULT_SIZE;
        }
        ClothSku sku = new ClothSku();
        sku.setItemId(item.getId());
        sku.setColor(c);
        sku.setSize(sz);
        sku.setStockQty(stockQty == null ? 0 : stockQty);
        skuMapper.insert(sku);

        return item.getId();
    }

    public List<ClothItem> listItems(String category, Integer status, String keyword) {
        UserContext.LoginUser user = UserContext.current();
        LambdaQueryWrapper<ClothItem> q = new LambdaQueryWrapper<ClothItem>()
                .eq(ClothItem::getShopId, user.getShopId())
                .eq(ClothItem::getIsDeleted, 0)
                .orderByDesc(ClothItem::getCreatedTime);
        if (category != null && !category.isEmpty()) q.eq(ClothItem::getCategory, category);
        if (status != null) q.eq(ClothItem::getStatus, status);
        if (keyword != null && !keyword.trim().isEmpty()) {
            q.like(ClothItem::getItemName, keyword.trim());
        }
        List<ClothItem> items = itemMapper.selectList(q);

        if (!items.isEmpty()) {
            List<Long> ids = items.stream().map(ClothItem::getId).collect(Collectors.toList());

            // 主图
            List<ClothImage> images = imageMapper.selectMainByItemIds(ids);
            java.util.Map<Long, String> urlMap = new java.util.LinkedHashMap<>();
            for (ClothImage img : images) {
                urlMap.putIfAbsent(img.getItemId(), img.getTosUrl());
            }
            items.forEach(item -> item.setMainImageUrl(
                    imageUtils.toPublicUrl(urlMap.get(item.getId()))
            ));

            // SKU：合计库存 + 主 SKU（id 最小，用于批量加库存/开单）
            List<ClothSku> allSkus = skuMapper.selectList(
                    new LambdaQueryWrapper<ClothSku>().in(ClothSku::getItemId, ids));
            Map<Long, List<ClothSku>> byItem = allSkus.stream()
                    .collect(Collectors.groupingBy(ClothSku::getItemId));
            for (ClothItem item : items) {
                List<ClothSku> skus = byItem.getOrDefault(item.getId(), Collections.emptyList());
                int sum = skus.stream()
                        .mapToInt(s -> s.getStockQty() == null ? 0 : s.getStockQty())
                        .sum();
                item.setStockQty(sum);
                ClothSku primary = skus.stream()
                        .min(Comparator.comparing(ClothSku::getId))
                        .orElse(null);
                item.setPrimarySkuId(primary != null ? primary.getId() : null);
            }
        }

        if (user.getCanSeeCost() == null || user.getCanSeeCost() == 0) {
            items.forEach(i -> i.setCostPrice(null));
        }
        return items;
    }

    public void updateStock(Long skuId, int delta) {
        ClothSku sku = skuMapper.selectById(skuId);
        if (sku == null) throw new RuntimeException("SKU不存在");
        sku.setStockQty(Math.max(0, sku.getStockQty() + delta));
        skuMapper.updateById(sku);
    }

    public List<ClothSku> listSkusByItem(Long itemId) {
        return skuMapper.selectList(
                new LambdaQueryWrapper<ClothSku>().eq(ClothSku::getItemId, itemId));
    }

    public void setItemStatus(Long itemId, int status) {
        ClothItem update = new ClothItem();
        update.setId(itemId);
        update.setStatus(status);
        itemMapper.updateById(update);
    }

    /** 软删除商品 */
    public void deleteItem(Long itemId) {
        UserContext.LoginUser user = UserContext.current();
        ClothItem item = itemMapper.selectById(itemId);
        if (item == null) throw new RuntimeException("商品不存在");
        if (!item.getShopId().equals(user.getShopId())) throw new RuntimeException("无权删除该商品");
        ClothItem upd = new ClothItem();
        upd.setId(itemId);
        upd.setIsDeleted(1);
        itemMapper.updateById(upd);
    }

    /** 批量软删除（同一店铺） */
    @Transactional
    public void deleteItemsBatch(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        UserContext.LoginUser user = UserContext.current();
        for (Long itemId : itemIds) {
            if (itemId == null) continue;
            ClothItem item = itemMapper.selectById(itemId);
            if (item == null) continue;
            if (!item.getShopId().equals(user.getShopId())) {
                throw new RuntimeException("无权删除商品：" + itemId);
            }
            ClothItem upd = new ClothItem();
            upd.setId(itemId);
            upd.setIsDeleted(1);
            itemMapper.updateById(upd);
        }
    }

    /**
     * 批量调整库存：对每个商品的主 SKU（id 最小）增加 delta。
     * 无 SKU 的商品跳过；delta 为负时库存不低于 0。
     */
    @Transactional
    public void batchAddStockForItems(List<Long> itemIds, int delta) {
        if (itemIds == null || itemIds.isEmpty() || delta == 0) return;
        UserContext.LoginUser user = UserContext.current();
        for (Long itemId : itemIds) {
            if (itemId == null) continue;
            ClothItem item = itemMapper.selectById(itemId);
            if (item == null || (item.getIsDeleted() != null && item.getIsDeleted() != 0)) continue;
            if (!item.getShopId().equals(user.getShopId())) {
                throw new RuntimeException("无权操作商品：" + itemId);
            }
            List<ClothSku> skus = listSkusByItem(itemId);
            if (skus.isEmpty()) continue;
            ClothSku primary = skus.stream().min(Comparator.comparing(ClothSku::getId)).orElse(null);
            if (primary != null) {
                updateStock(primary.getId(), delta);
            }
        }
    }

    private static String normStr(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static final String DEFAULT_SIZE = "均码";

    // 常见颜色关键词（用于从 itemName 中反推颜色）
    private static final String[] COLOR_KWS = new String[]{
            "混色", "花色", "条纹", "格子",
            "黑色", "白色", "米白", "米色",
            "棕色", "咖色", "驼色", "褐色",
            "灰色", "银灰",
            "红色", "酒红", "玫红", "粉色", "紫色",
            "蓝色", "深蓝", "浅蓝", "藏蓝",
            "绿色", "墨绿", "橄榄绿",
            "黄色", "姜黄", "橙色",
    };

    private static String extractColorFromText(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        for (String kw : COLOR_KWS) {
            if (s.contains(kw)) return kw;
        }
        return null;
    }

    /**
     * 识别进货小票图片并返回结构化 JSON。
     */
    public Map<String, Object> parseReceipt(MultipartFile image) throws Exception {
        String prompt = receiptParsePrompt != null ? receiptParsePrompt : RECEIPT_PARSE_PROMPT_FALLBACK;
        long t0 = System.currentTimeMillis();
        String raw;
        int tokenUsed = 0;
        try {
            AliyunUtils.QwenTextUsageResult r = aliyunUtils.qWenVLPlusWithUsage(image, prompt);
            raw = r.getText();
            tokenUsed = r.getTokenUsed() == null ? 0 : r.getTokenUsed();
            usageLogService.record("receipt_parse", tokenUsed);
        } catch (Exception e) {
            log.error("[进货单识别] 失败耗时={}ms msg={}", System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }

        raw = raw == null ? "" : raw.trim();
        // 兜底：截取 JSON 包裹（避免模型输出前后带解释文本）
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            raw = raw.substring(start, end + 1);
        }

        JSONObject obj;
        try {
            obj = JSON.parseObject(raw);
        } catch (Exception e) {
            throw new RuntimeException("[进货单识别] 返回内容无法解析为 JSON，请检查模型输出。");
        }

        String orderNo = obj.getString("orderNo");
        if (orderNo == null) orderNo = "";

        JSONArray items = obj.getJSONArray("items");
        List<Map<String, Object>> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                if (it == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lineNo", it.getInteger("lineNo"));
                m.put("itemName", it.getString("itemName"));
                m.put("category", it.getString("category"));
                m.put("color", it.getString("color"));
                m.put("size", it.getString("size"));
                m.put("qty", it.getInteger("qty"));
                m.put("unitCostPrice", it.getBigDecimal("unitCostPrice"));
                list.add(m);
            }
        }

        log.info("[进货单识别] 耗时={}ms orderNo={} items={}", System.currentTimeMillis() - t0, orderNo, list.size());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderNo", orderNo);
        resp.put("items", list);
        return resp;
    }

    /**
     * 一键入库：上传进货单图片 -> AI 识别 -> 按明细行逐条创建商品 + SKU 并写入库存。
     * 规则：默认“一行=一个新商品”，qty 为识别出的最终数量（已处理数量×n）。
     */
    @Transactional
    public Map<String, Object> importReceipt(MultipartFile image) throws Exception {
        Map<String, Object> parsed = parseReceipt(image);
        String orderNo = (String) parsed.get("orderNo");
        Object rawItems = parsed.get("items");
        if (!(rawItems instanceof List)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("orderNo", orderNo == null ? "" : orderNo);
            empty.put("createdCount", 0);
            empty.put("itemIds", Collections.emptyList());
            return empty;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;

        if (items == null || items.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("orderNo", orderNo == null ? "" : orderNo);
            empty.put("createdCount", 0);
            empty.put("itemIds", Collections.emptyList());
            return empty;
        }

        UserContext.LoginUser user = UserContext.current();

        int createdCount = 0;
        List<Long> itemIds = new ArrayList<>();

        for (Map<String, Object> line : items) {
            String itemName = normStr((String) line.get("itemName"));
            if (itemName == null) itemName = "未命名商品";

            String category = normStr((String) line.get("category"));
            String color    = normStr((String) line.get("color"));
            // 你的规则：尺码默认都是“均码”
            String size     = normStr((String) line.get("size"));
            if (size == null) size = DEFAULT_SIZE;

            // 你的规则：AI 的 color 字段有时会混进品名，颜色从 itemName 里提取关键词
            // 优先使用显式 color；如果 color 为空/提取不到则尝试 itemName 反推
            if (color == null || color.isEmpty()) {
                color = extractColorFromText(itemName);
            } else {
                // color 可能包含品名，仍尝试从 itemName 中提取一个更干净的颜色关键词
                String c2 = extractColorFromText(itemName);
                if (c2 != null) color = c2;
            }

            Integer qty = line.get("qty") == null ? 0 : ((Number) line.get("qty")).intValue();
            BigDecimal unitCostPrice = (BigDecimal) line.get("unitCostPrice");

            // 逐行视为“新商品入库”（不做合并）
            ClothItem item = new ClothItem();
            item.setShopId(user.getShopId());
            item.setItemNo(genItemNo());
            item.setItemName(itemName);
            item.setCategory(category);
            item.setCostPrice(unitCostPrice);
            item.setSalePrice(null);
            item.setDescription(null);
            item.setStatus(1);
            item.setCreatedBy(user.getUserId());
            item.setCreatedTime(new Date());
            itemMapper.insert(item);

            // SKU：允许 color/size 为空，但必须插入一条 SKU 以承载 stock_qty
            ClothSku sku = new ClothSku();
            sku.setItemId(item.getId());
            sku.setColor(color);
            sku.setSize(size);
            sku.setStockQty(qty == null ? 0 : qty);
            skuMapper.insert(sku);

            createdCount++;
            itemIds.add(item.getId());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderNo", orderNo == null ? "" : orderNo);
        resp.put("createdCount", createdCount);
        resp.put("itemIds", itemIds);
        return resp;
    }
}
